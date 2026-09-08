package uz.murodjon.robotcallv2.agent.rtp;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uz.murodjon.robotcallv2.agent.audio.AmbientSoundGenerator;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.TelephonyNoiseCanceller;
import uz.murodjon.robotcallv2.agent.codec.G711Codec;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One RTP UDP listener + sender for a single call, sharing a single NIO datagram socket.
 *
 * <p>Inbound: Packet Loss Concealment to smoothly recover from missing network packets,
 * and {@link TelephonyNoiseCanceller} on the copy handed to the recognizer — never on the
 * copy written to the recording.
 *
 * <p>Outbound: the room the bot is calling from, and the typing under the pause while a
 * reply is composed, both mixed under the voice by {@link AmbientSoundGenerator}.
 */
public class RtpEndpoint implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RtpEndpoint.class);

    private static final int PT_PCMU = 0; // µ-law
    private static final int PT_PCMA = 8; // A-law
    private static final int QUEUE_CAPACITY = 2000;

    /** RTP clock of G.711 telephony audio — the unit sequence/timestamp arithmetic uses. */
    private static final int CLOCK_RATE = 8000;

    private static final int SAMPLES_PER_FRAME = 160; // 20 ms @ 8 kHz
    private static final int FRAME_MS = 20;

    /**
     * Silence frames sent after the queue runs dry before the pacer stops (25 frames =
     * 500 ms). Streamed synthesis and sentence-by-sentence replies leave gaps of a few
     * hundred milliseconds between chunks; at 100 ms the pacer stopped inside those gaps
     * and every restart added its pre-buffer delay on top of the gap itself.
     */
    private static final int MAX_IDLE_FRAMES = 25;

    /**
     * Half the 16-bit sequence space. A modular distance above this is the short way
     * backwards, i.e. the packet is late rather than the stream having jumped forward.
     */
    private static final int SEQ_HALF = 0x8000;

    /** Full calibrated bed level — see {@code AmbientSoundGenerator} for what that is. */
    private static final double DEFAULT_AMBIENT_VOLUME = 1.0;

    /**
     * A bed that is simply there from the first frame is heard as an artefact of the
     * connection; one that arrives over a second and a half is heard as the room opening up.
     */
    private static final double DEFAULT_AMBIENT_FADE_IN_SECONDS = 1.5;

    /** Short, because a pause is short: typing that faded like the room bed would miss it. */
    private static final double THINKING_FADE_IN_SECONDS = 0.3;

    private final int port;
    private final RtpCodec codec;
    private final WavRecorder recorder;
    /**
     * Volatile because the chain can be swapped on a live endpoint ({@link
     * #replaceListeners}) while the RTP thread is walking it.
     */
    private volatile List<AudioListener> listeners;
    /** Tap for the bot's own outgoing frames (live "listen in", §10.3); null if nobody taps it. */
    private final AudioListener outboundTap;
    private final BlockingQueue<ReceivedPacket> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final RtpStats stats = new RtpStats(CLOCK_RATE);
    private final long ssrc = Integer.toUnsignedLong(new Random().nextInt());

    /** Pending playback audio, oldest first. Guarded by {@link #playLock}. */
    private final Deque<short[]> playQueue = new ArrayDeque<>();
    private final Object playLock = new Object();
    private short[] currentChunk;   // guarded by playLock
    private int currentOffset;      // guarded by playLock
    private int idleFrames;         // guarded by playLock
    /** Samples ever queued, and samples ever sent — the two ends of "did they hear it". */
    private long queuedSamples;     // guarded by playLock
    private long playedSamples;     // guarded by playLock

    // Inbound noise cancellation, applied to what the recognizer hears.
    private final TelephonyNoiseCanceller noiseCanceller = new TelephonyNoiseCanceller();
    private volatile boolean noiseCancellationEnabled = true;
    private volatile NoiseCancellationMode noiseCancellationMode = NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;

    /**
     * The room the bot is calling from, and how loud it is. Volume is a multiplier on the
     * level {@code AmbientSoundGenerator} already calibrated each bed to, so 1.0 is that
     * tuned level rather than "as loud as possible".
     */
    private volatile AmbientSound ambientSound = AmbientSound.OFF;
    private volatile double ambientSoundVolume = DEFAULT_AMBIENT_VOLUME;
    private volatile double ambientSoundFadeInSeconds = DEFAULT_AMBIENT_FADE_IN_SECONDS;

    /** Typing under the pause while a reply is being composed; off outside that window. */
    private volatile AmbientSound thinkingSound = AmbientSound.OFF;
    private volatile double thinkingSoundVolume = DEFAULT_AMBIENT_VOLUME;
    private final AtomicBoolean thinkingActive = new AtomicBoolean(false);
    private long thinkingStartSample;

    private final AtomicBoolean pacerRunning = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pacer;

    private volatile boolean running = true;
    private volatile InetSocketAddress remoteAddress;
    private volatile boolean remoteLatched;
    private io.netty.channel.Channel channel;
    private Thread consumer;

    // Outgoing RTP counters
    private int sendSeq;
    private long sendTimestamp;

    public RtpEndpoint(int port, RtpCodec codec, WavRecorder recorder, List<AudioListener> listeners) {
        this(port, codec, recorder, listeners, null);
    }

    public RtpEndpoint(int port, RtpCodec codec, WavRecorder recorder, List<AudioListener> listeners,
                       AudioListener outboundTap) {
        this.port = port;
        this.codec = codec;
        this.recorder = recorder;
        this.listeners = listeners == null ? List.of() : List.copyOf(listeners);
        this.outboundTap = outboundTap;
    }

    /**
     * Swap the listener chain on a live endpoint. Needed when the engine chosen at answer
     * time cannot start and the call carries on with the other pipeline: the two listen
     * through entirely different chains, and a realtime call carries no STT stream at all,
     * so appending is not enough — the chain is replaced.
     *
     * <p>Replaced wholesale rather than mutated: the RTP thread walks this list for every
     * packet, and handing it a new immutable list through a volatile field is what keeps
     * that walk safe without putting a lock on the hot path.
     */
    public void replaceListeners(List<AudioListener> replacement) {
        this.listeners = replacement == null ? List.of() : List.copyOf(replacement);
    }

    public void setNoiseCancellation(boolean enabled, NoiseCancellationMode mode) {
        this.noiseCancellationEnabled = enabled;
        this.noiseCancellationMode = mode != null ? mode : NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;
        this.noiseCanceller.configure(enabled, this.noiseCancellationMode);
    }

    public void setAmbientSound(AmbientSound ambientSound, Double volume, Double fadeInSeconds) {
        this.ambientSound = ambientSound != null ? ambientSound : AmbientSound.OFF;
        this.ambientSoundVolume = clampVolume(volume);
        this.ambientSoundFadeInSeconds = fadeInSeconds != null
                ? Math.max(0.0, fadeInSeconds) : DEFAULT_AMBIENT_FADE_IN_SECONDS;
        if (this.ambientSound != AmbientSound.OFF && remoteAddress != null) {
            startPacer();
        }
    }

    public void setThinkingSound(AmbientSound thinkingSound, Double volume) {
        this.thinkingSound = thinkingSound != null ? thinkingSound : AmbientSound.OFF;
        this.thinkingSoundVolume = clampVolume(volume);
    }

    private static double clampVolume(Double volume) {
        return volume != null ? Math.max(0.0, Math.min(1.0, volume)) : DEFAULT_AMBIENT_VOLUME;
    }

    public void startThinking() {
        if (thinkingSound != null && thinkingSound != AmbientSound.OFF) {
            thinkingStartSample = sendTimestamp;
            thinkingActive.set(true);
            if (remoteAddress != null) {
                startPacer();
            }
        }
    }

    public void stopThinking() {
        thinkingActive.set(false);
    }

    public int port() {
        return port;
    }

    public void bind(EventLoopGroup group) {
        Bootstrap b = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                        if (!remoteLatched) {
                            remoteAddress = msg.sender();
                            remoteLatched = true;
                            log.info("RTP peer for port {} latched to {} (from inbound traffic)", port, remoteAddress);
                            if (ambientSound != AmbientSound.OFF || thinkingActive.get()) {
                                startPacer();
                            }
                        }
                        ByteBuf content = msg.content();
                        byte[] bytes = new byte[content.readableBytes()];
                        content.readBytes(bytes);
                        long arrivalNanos = System.nanoTime();
                        if (!queue.offer(new ReceivedPacket(bytes, arrivalNanos))) {
                            log.warn("RTP receive queue on port {} full; packet dropped", port);
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        log.warn("RTP channel error on port {}: {}", port, cause.getMessage());
                    }
                });

        try {
            channel = b.bind(port).sync().channel();
            log.info("RTP endpoint bound to UDP port {}", port);
            if ((ambientSound != AmbientSound.OFF || thinkingActive.get()) && remoteAddress != null) {
                startPacer();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bind RTP port " + port, e);
        }

        consumer = Thread.ofVirtual().name("rtp-in-" + port).start(this::consume);
    }

    public RtpStats stats() {
        return stats;
    }

    public void setRemote(InetSocketAddress remote) {
        if (remote == null || remote.getAddress() == null || remote.getPort() <= 0) {
            return;
        }
        if (remoteLatched) {
            return;
        }
        remoteAddress = remote;
        log.info("RTP peer for port {} set to {} (from Asterisk)", port, remote);
        if (ambientSound != AmbientSound.OFF || thinkingActive.get()) {
            startPacer();
        }
    }

    public void setRemoteAddress(String ip, int port) {
        if (ip == null || ip.isBlank() || port <= 0) {
            return;
        }
        try {
            setRemote(new InetSocketAddress(ip, port));
        } catch (Exception e) {
            log.warn("Invalid remote address {}:{} on port {}: {}", ip, port, this.port, e.getMessage());
        }
    }

    public void playPcm(short[] pcm) {
        flushPlayback();
        enqueuePcm(pcm);
    }

    public void enqueuePcm(short[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        if (awaitRemote() == null) {
            log.warn("No RTP peer on port {} yet; dropping {} samples", port, pcm.length);
            return;
        }
        synchronized (playLock) {
            playQueue.addLast(pcm);
            queuedSamples += pcm.length;
            idleFrames = 0;
        }
        startPacer();
    }

    public void flushPlayback() {
        synchronized (playLock) {
            playQueue.clear();
            currentChunk = null;
            currentOffset = 0;
            queuedSamples = playedSamples;
            idleFrames = 0;
        }
        if (ambientSound == AmbientSound.OFF && !thinkingActive.get()) {
            stopPacer();
        }
    }

    public long queuedSamples() {
        synchronized (playLock) {
            return queuedSamples;
        }
    }

    public long playedSamples() {
        synchronized (playLock) {
            return playedSamples;
        }
    }

    public boolean isPlaying() {
        if (!pacerRunning.get()) {
            return false;
        }
        synchronized (playLock) {
            return !playQueue.isEmpty() || (currentChunk != null && currentOffset < currentChunk.length);
        }
    }

    private InetSocketAddress awaitRemote() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (remoteAddress == null && System.nanoTime() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return remoteAddress;
    }

    private void startPacer() {
        if (channel == null || channel.eventLoop() == null) {
            return;
        }
        if (pacerRunning.compareAndSet(false, true)) {
            // Pre-buffer 60-80ms (3-4 frames) to smooth out streaming jitter and prevent underrun
            pacer = channel.eventLoop().scheduleAtFixedRate(
                    this::sendFrame, FRAME_MS * 3, FRAME_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void stopPacer() {
        if (pacerRunning.compareAndSet(true, false)) {
            ScheduledFuture<?> p = pacer;
            if (p != null) {
                p.cancel(false);
                pacer = null;
            }
        }
    }

    private void sendFrame() {
        InetSocketAddress remote = remoteAddress;
        if (!running || remote == null) {
            stopPacer();
            return;
        }
        byte[] payload = new byte[SAMPLES_PER_FRAME];
        short[] pcmFrame = new short[SAMPLES_PER_FRAME];
        int filled = 0;
        boolean first;
        synchronized (playLock) {
            while (filled < SAMPLES_PER_FRAME) {
                if (currentChunk == null || currentOffset >= currentChunk.length) {
                    currentChunk = playQueue.pollFirst();
                    currentOffset = 0;
                    if (currentChunk == null) {
                        break;
                    }
                }
                int n = Math.min(SAMPLES_PER_FRAME - filled, currentChunk.length - currentOffset);
                for (int i = 0; i < n; i++) {
                    short sample = currentChunk[currentOffset + i];
                    payload[filled + i] = codec.encode(sample);
                    pcmFrame[filled + i] = sample;
                }
                filled += n;
                currentOffset += n;
            }
            playedSamples += filled;
            if (filled == 0) {
                idleFrames++;
                if (idleFrames >= MAX_IDLE_FRAMES && ambientSound == AmbientSound.OFF && !thinkingActive.get()) {
                    stopPacer();
                    return;
                }
                for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
                    payload[i] = codec.silence();
                }
            } else {
                idleFrames = 0;
                for (int i = filled; i < SAMPLES_PER_FRAME; i++) {
                    payload[i] = codec.silence();
                }
            }
        }

        boolean bedPlaying = ambientSound != null && ambientSound != AmbientSound.OFF;
        if (bedPlaying) {
            pcmFrame = AmbientSoundGenerator.mix(pcmFrame, ambientSound, sendTimestamp,
                    ambientSoundVolume, ambientSoundFadeInSeconds);
        }

        // Typing is timed from when the pause began, not from the call, so the fade runs
        // once per pause instead of once per call.
        boolean thinkingPlaying = thinkingActive.get()
                && thinkingSound != null && thinkingSound != AmbientSound.OFF;
        if (thinkingPlaying) {
            long offset = Math.max(0, sendTimestamp - thinkingStartSample);
            pcmFrame = AmbientSoundGenerator.mix(pcmFrame, thinkingSound, offset,
                    thinkingSoundVolume, THINKING_FADE_IN_SECONDS);
        }

        if (bedPlaying || thinkingPlaying) {
            for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
                payload[i] = codec.encode(pcmFrame[i]);
            }
        }

        recorder.writeBot(pcmFrame, SAMPLES_PER_FRAME);
        if (outboundTap != null) {
            outboundTap.onAudio(pcmFrame, SAMPLES_PER_FRAME);
        }
        first = sendSeq == 0;
        try {
            byte[] rtp = RtpPacket.toBytes(codec.payloadType(), sendSeq & 0xFFFF, sendTimestamp, ssrc, first, payload);
            channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(rtp), remote));
            sendSeq++;
            sendTimestamp += SAMPLES_PER_FRAME;
        } catch (Exception e) {
            log.warn("RTP send error on port {}: {}", port, e.getMessage());
            flushPlayback();
        }
    }

    private void consume() {
        short[] pcm = new short[512];
        short[] lastGoodFrame = new short[SAMPLES_PER_FRAME];
        int lastSeq = -1;

        while (running || !queue.isEmpty()) {
            ReceivedPacket received;
            try {
                received = queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (received == null) {
                continue;
            }
            try {
                byte[] raw = received.data();
                RtpPacket p = RtpPacket.parse(raw, raw.length);
                int seq = p.sequenceNumber();
                stats.onPacket(seq, p.timestamp(), received.arrivalNanos());

                // Sequence numbers are 16-bit and wrap, and packets can arrive late or
                // twice. Compared with plain arithmetic ("seq > lastSeq + 1") a wrap read
                // as a 65 000-packet gap and a late packet read as none, after which the
                // next packet in order looked like a gap and had PLC invented for it — so
                // one reordered packet produced a burst of made-up audio. Distance is
                // measured modulo the sequence space instead, and only a genuine forward
                // gap can conceal anything.
                int gap = lastSeq < 0 ? 1 : (seq - lastSeq) & 0xFFFF;
                if (lastSeq >= 0 && (gap == 0 || gap > SEQ_HALF)) {
                    // A duplicate, or a packet whose successor already went through. There
                    // is no jitter buffer to re-order it into, and inserting it now would
                    // put those 20 ms in the wrong place in the recognizer's stream —
                    // which is worse than the gap PLC has already covered. RtpStats counts
                    // it either way, so the loss stays visible.
                    continue;
                }

                // Packet Loss Concealment (PLC): interpolate missing 1-2 frames using decaying energy
                if (lastSeq >= 0 && gap > 1 && gap <= 3) {
                    int lostCount = gap - 1;
                    short[] plcFrame = new short[SAMPLES_PER_FRAME];
                    for (int k = 0; k < lostCount; k++) {
                        float decay = (float) Math.pow(0.65, k + 1);
                        for (int i = 0; i < SAMPLES_PER_FRAME; i++) {
                            plcFrame[i] = (short) Math.round(lastGoodFrame[i] * decay);
                        }
                        recorder.writeCaller(plcFrame, SAMPLES_PER_FRAME);
                        short[] cleanPlcFrame = noiseCancellationEnabled
                                ? noiseCanceller.process(plcFrame, SAMPLES_PER_FRAME) : plcFrame;
                        for (AudioListener listener : listeners) {
                            listener.onAudio(cleanPlcFrame, SAMPLES_PER_FRAME);
                        }
                    }
                }
                lastSeq = seq;

                byte[] payload = p.payload();
                if (payload.length > pcm.length) {
                    pcm = new short[payload.length];
                }
                switch (p.payloadType()) {
                    case PT_PCMU -> G711Codec.ulawToPcm(payload, payload.length, pcm);
                    case PT_PCMA -> G711Codec.alawToPcm(payload, payload.length, pcm);
                    default -> {
                        continue;
                    }
                }

                int copyLen = Math.min(payload.length, lastGoodFrame.length);
                System.arraycopy(pcm, 0, lastGoodFrame, 0, copyLen);

                // The recording keeps what the caller actually sent: it is evidence of the
                // call, and a filter that guesses wrong would have erased the proof of it.
                // Only the recognizer's copy is cleaned.
                recorder.writeCaller(pcm, payload.length);
                short[] cleanPcm = noiseCancellationEnabled
                        ? noiseCanceller.process(pcm, payload.length) : pcm;
                for (AudioListener listener : listeners) {
                    listener.onAudio(cleanPcm, payload.length);
                }
            } catch (Exception e) {
                log.warn("RTP processing error on port {}: {}", port, e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
        stopPacer();
        flushPlayback();
        if (consumer != null) {
            try {
                consumer.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            recorder.close();
        } catch (Exception e) {
            log.warn("Failed to finalize recording on port {}: {}", port, e.getMessage());
        }
        for (AudioListener listener : listeners) {
            if (listener instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    log.warn("Failed to close audio listener on port {}: {}", port, e.getMessage());
                }
            }
        }
        if (channel != null) {
            channel.close();
        }
        log.info("RTP endpoint on port {} closed", port);
    }

    private record ReceivedPacket(byte[] data, long arrivalNanos) {
    }
}
