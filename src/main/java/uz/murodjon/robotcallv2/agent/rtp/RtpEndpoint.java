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
import uz.murodjon.robotcallv2.agent.audio.NeuralDenoiseStream;
import uz.murodjon.robotcallv2.agent.audio.TelephonyNoiseCanceller;
import uz.murodjon.robotcallv2.agent.codec.G711Codec;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.NoiseCancellationMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.util.*;
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
    private static final int SEQ_MODULUS = 0x10000;
    /** How far behind a packet may be and still count as late rather than a new stream — RtpStats uses the same. */
    private static final int MAX_MISORDER = 100;

    /** Full calibrated bed level — see {@code AmbientSoundGenerator} for what that is. */
    private static final double DEFAULT_AMBIENT_VOLUME = 1.0;

    /**
     * A bed that is simply there from the first frame is heard as an artefact of the
     * connection; one that arrives over a second and a half is heard as the room opening up.
     */
    private static final double DEFAULT_AMBIENT_FADE_IN_SECONDS = 1.5;

    /** Short, because a pause is short: typing that faded like the room bed would miss it. */
    private static final double THINKING_FADE_IN_SECONDS = 0.3;

    /**
     * The call this endpoint carries. Held only so its logs can name it ({@link CallMdc}):
     * the port already identifies the endpoint, but nothing on the audio path runs on the
     * thread that set up the call, so a reader had to map port back to call by hand.
     */
    private final String channelId;
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
    /** Inbound packets waiting for the worker; sized by {@code rtp.receive-queue-capacity}. */
    private final BlockingQueue<ReceivedPacket> queue;
    /** The most playback audio held unsent, in samples — {@code rtp.max-queued-playback-seconds}. */
    private final long maxQueuedSamples;
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
    /** The neural stage, when the deployment has one; replaces the broadband gate while it works. */
    private volatile NeuralDenoiseStream neuralDenoiser;

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

    /**
     * How far back {@link #recentOutboundLevel()} looks: long enough to cover the echo
     * path of a PSTN call (the bot's voice comes back 50–400 ms after it was sent).
     */
    private static final int OUTBOUND_LEVEL_FRAMES = 25;
    /**
     * RMS of the last frames sent, and when each went out — a ring written by the pacer
     * and read by the RTP consumer. Racy on purpose: it feeds an echo heuristic, and a
     * frame's worth of staleness changes nothing there.
     */
    private final double[] outboundLevels = new double[OUTBOUND_LEVEL_FRAMES];
    private final long[] outboundLevelNanos = new long[OUTBOUND_LEVEL_FRAMES];
    private int outboundLevelIndex;

    private final AtomicBoolean pacerRunning = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pacer;

    private volatile boolean running = true;
    /** See {@link #pausePlayback}. */
    private volatile boolean paused;
    private volatile InetSocketAddress remoteAddress;
    private volatile boolean remoteLatched;
    /** Whether {@link #remoteAddress} is the one Asterisk reported for this leg. */
    private volatile boolean remoteFromAsterisk;
    /**
     * Set after the first failed write: the call goes on unrecorded rather than unheard.
     * Volatile because an operator call's taps write from their own threads.
     */
    private volatile boolean recorderFailed;
    /** Off while an operator call's taps record both sides instead of this leg's mixed audio. */
    private volatile boolean recordingInbound = true;
    private io.netty.channel.Channel channel;
    private Thread consumer;

    // Outgoing RTP counters
    private int sendSeq;
    private long sendTimestamp;

    public RtpEndpoint(String channelId, int port, RtpCodec codec, WavRecorder recorder,
                       List<AudioListener> listeners, int receiveQueueCapacity, int maxQueuedPlaybackSeconds) {
        this(channelId, port, codec, recorder, listeners, null, receiveQueueCapacity, maxQueuedPlaybackSeconds);
    }

    public RtpEndpoint(String channelId, int port, RtpCodec codec, WavRecorder recorder,
                       List<AudioListener> listeners, AudioListener outboundTap, int receiveQueueCapacity,
                       int maxQueuedPlaybackSeconds) {
        this.queue = new LinkedBlockingQueue<>(receiveQueueCapacity);
        this.maxQueuedSamples = (long) maxQueuedPlaybackSeconds * CLOCK_RATE;
        this.channelId = channelId;
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
    public synchronized void replaceListeners(List<AudioListener> replacement) {
        this.listeners = replacement == null ? List.of() : List.copyOf(replacement);
    }

    /**
     * Add one listener to a live endpoint, after the ones already there — for a consumer
     * only some calls need, and only from some point on (a voicemail's beep). Copied the
     * same way as {@link #replaceListeners}; synchronized with it so neither change is lost.
     */
    public synchronized void addListener(AudioListener listener) {
        List<AudioListener> next = new ArrayList<>(listeners);
        next.add(listener);
        this.listeners = List.copyOf(next);
    }

    public void setNoiseCancellation(boolean enabled, NoiseCancellationMode mode) {
        this.noiseCancellationEnabled = enabled;
        this.noiseCancellationMode = mode != null ? mode : NoiseCancellationMode.BACKGROUND_NOISE_SUPPRESSION;
        this.noiseCanceller.configure(enabled, this.noiseCancellationMode);
    }

    /**
     * Clean the recognizer's copy with the neural denoiser instead of the broadband gate.
     * Still governed by {@link #setNoiseCancellation}: off there means off here too.
     */
    public void setNeuralDenoiser(NeuralDenoiseStream neuralDenoiser) {
        this.neuralDenoiser = neuralDenoiser;
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
                        // The call goes into the logging context around the branches that
                        // log, not around the whole callback: this runs on the shared
                        // Netty loop fifty times a second per call, and both branches
                        // below are already off the steady path — the first fires once,
                        // the second only when the queue has backed up.
                        if (!remoteLatched) {
                            // Asterisk's own report of the leg (setRemote) wins over whoever
                            // sent first: a port is reused as soon as it is freed, and the
                            // first packet can come from the previous call's leg still
                            // dying — latched to that, this call's caller never heard the bot.
                            if (!remoteFromAsterisk) {
                                remoteAddress = msg.sender();
                            }
                            remoteLatched = true;
                            CallMdc.with(channelId, () -> log.info(
                                    "RTP peer for port {} latched to {} (first packet from {})",
                                    port, remoteAddress, msg.sender()));
                            if (ambientSound != AmbientSound.OFF || thinkingActive.get() || hasQueuedAudio()) {
                                startPacer();
                            }
                        }
                        ByteBuf content = msg.content();
                        byte[] bytes = new byte[content.readableBytes()];
                        content.readBytes(bytes);
                        long arrivalNanos = System.nanoTime();
                        if (!queue.offer(new ReceivedPacket(bytes, arrivalNanos))) {
                            CallMdc.with(channelId, () -> log.warn(
                                    "RTP receive queue on port {} full; packet dropped", port));
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        CallMdc.with(channelId, () -> log.warn(
                                "RTP channel error on port {}: {}", port, cause.getMessage()));
                    }
                });

        try {
            channel = b.bind(port).sync().channel();
            log.info("RTP endpoint bound to UDP port {}", port);
            if ((ambientSound != AmbientSound.OFF || thinkingActive.get()) && remoteAddress != null) {
                startPacer();
            }
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.RTP_BIND_FAILED, "rtp", e, port);
        }

        // Set once for the whole thread rather than per packet: this one belongs to this
        // call and to nothing else, for as long as the call lasts.
        consumer = Thread.ofVirtual().name("rtp-in-" + port)
                .start(() -> CallMdc.with(channelId, this::consume));
    }

    public RtpStats stats() {
        return stats;
    }

    public void setRemote(InetSocketAddress remote) {
        if (remote == null || remote.getAddress() == null || remote.getPort() <= 0
                || remote.getAddress().isAnyLocalAddress()) {
            return;
        }
        // Taken even after a latch: what Asterisk reports is the leg it set up for this
        // call, while the latch only knows who sent first.
        InetSocketAddress previous = remoteAddress;
        remoteAddress = remote;
        remoteFromAsterisk = true;
        if (previous != null && !previous.equals(remote)) {
            log.warn("RTP peer for port {} moved from {} to {} (from Asterisk)", port, previous, remote);
        } else {
            log.info("RTP peer for port {} set to {} (from Asterisk)", port, remote);
        }
        if (ambientSound != AmbientSound.OFF || thinkingActive.get() || hasQueuedAudio()) {
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

    /**
     * Queues audio for the pacer. Never blocks: this is called from a provider's WebSocket
     * thread and from turn workers, and neither may wait on the far end. Audio queued
     * before the RTP peer is known simply waits in the queue — the pacer starts the moment
     * the peer latches. The queue is capped so a producer that runs away from the line
     * (an engine streaming minutes of audio into a dead call) cannot grow the heap: past
     * the cap the newest audio is dropped and counted.
     */
    public void enqueuePcm(short[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        synchronized (playLock) {
            if (queuedSamples - playedSamples + pcm.length > maxQueuedSamples) {
                log.warn("Playback queue on port {} is {} s deep; dropping {} samples", port,
                        (queuedSamples - playedSamples) / CLOCK_RATE, pcm.length);
                return;
            }
            playQueue.addLast(pcm);
            queuedSamples += pcm.length;
            idleFrames = 0;
        }
        if (remoteAddress != null) {
            startPacer();
        }
    }

    private boolean hasQueuedAudio() {
        synchronized (playLock) {
            return !playQueue.isEmpty() || (currentChunk != null && currentOffset < currentChunk.length);
        }
    }

    /**
     * Hold the bot's audio where it is without discarding it: the line carries silence
     * (and the ambient bed) and nothing queued is consumed, so {@link #resumePlayback}
     * carries on from the very sample it stopped at. Used while a barge-in is still being
     * confirmed — a cough should cost the caller a pause, not the rest of the sentence.
     */
    public void pausePlayback() {
        paused = true;
    }

    public void resumePlayback() {
        paused = false;
        if (remoteAddress != null && hasQueuedAudio()) {
            startPacer();
        }
    }

    public void flushPlayback() {
        synchronized (playLock) {
            playQueue.clear();
            currentChunk = null;
            currentOffset = 0;
            queuedSamples = playedSamples;
            idleFrames = 0;
            paused = false;
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

    /**
     * The loudest the bot has been on the wire within the echo window, as frame RMS — 0 when
     * it has sent nothing lately. What an echo of it arriving now could at most be.
     */
    public double recentOutboundLevel() {
        long horizon = System.nanoTime() - (long) OUTBOUND_LEVEL_FRAMES * FRAME_MS * 1_000_000L;
        double peak = 0;
        for (int i = 0; i < OUTBOUND_LEVEL_FRAMES; i++) {
            if (outboundLevelNanos[i] >= horizon && outboundLevels[i] > peak) {
                peak = outboundLevels[i];
            }
        }
        return peak;
    }

    private void recordOutboundLevel(short[] frame) {
        double sumSq = 0;
        for (short sample : frame) {
            sumSq += (double) sample * sample;
        }
        int slot = outboundLevelIndex;
        outboundLevels[slot] = Math.sqrt(sumSq / frame.length);
        outboundLevelNanos[slot] = System.nanoTime();
        outboundLevelIndex = (slot + 1) % OUTBOUND_LEVEL_FRAMES;
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
            // Paused: nothing is taken off the queue, and the frame goes out as silence.
            // Not counted as idle either — the pacer has to be running for the resume.
            while (!paused && filled < SAMPLES_PER_FRAME) {
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
                if (!paused) {
                    idleFrames++;
                }
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

        recordOutboundLevel(pcmFrame);
        if (recorder != null) {
            recorder.writeBot(pcmFrame, SAMPLES_PER_FRAME);
        }
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
            // Also on the Netty loop (the pacer runs there), so the context goes on here
            // rather than around every frame sent.
            CallMdc.with(channelId, () -> log.warn("RTP send error on port {}: {}", port, e.getMessage()));
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
                RtpPacket p = RtpPacket.tryParse(raw, raw.length);
                if (p == null) {
                    // Malformed input off the network, not a fault of ours: dropped like a lost packet.
                    log.debug("Dropped a malformed RTP packet ({} bytes) on port {}", raw.length, port);
                    continue;
                }
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
                if (lastSeq >= 0 && gap > SEQ_HALF && SEQ_MODULUS - gap > MAX_MISORDER) {
                    // Far behind, not just late: a new stream (a re-INVITE, a new SSRC)
                    // restarting its sequence below the old one. Treated as reordering, every
                    // packet of it was dropped until the numbers caught up — up to 32 000
                    // packets, eleven minutes of a deaf call. Resync instead, as RtpStats does.
                    lastSeq = -1;
                    gap = 1;
                }
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
                        recordCaller(plcFrame, SAMPLES_PER_FRAME);
                        short[] cleanPlcFrame = clean(plcFrame, SAMPLES_PER_FRAME);
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
                recordCaller(pcm, payload.length);
                short[] cleanPcm = clean(pcm, payload.length);
                for (AudioListener listener : listeners) {
                    listener.onAudio(cleanPcm, payload.length);
                }
            } catch (Exception e) {
                log.warn("RTP processing error on port {}: {}", port, e.getMessage());
            }
        }
    }

    /**
     * The recognizer's copy of a frame: neural when there is a working model, else the
     * broadband gate, else as received.
     */
    private short[] clean(short[] pcm, int length) {
        if (!noiseCancellationEnabled) {
            return pcm;
        }
        NeuralDenoiseStream neural = neuralDenoiser;
        if (neural != null && noiseCancellationMode != NoiseCancellationMode.OFF) {
            short[] cleaned = neural.process(pcm, length);
            if (cleaned != null) {
                return cleaned;
            }
            neuralDenoiser = null;
        }
        return noiseCanceller.process(pcm, length);
    }

    private void recordCaller(short[] pcm, int length) {
        if (recordingInbound) {
            recordCallerSide(pcm, length);
        }
    }

    /**
     * Whether this leg's own inbound audio goes into the recording. Switched off while an
     * operator is on the call: this leg then hears the client and the operator mixed, and the
     * two are recorded from their own taps instead ({@link #recordCallerSide}, {@link #recordOtherSide}).
     */
    public void setRecordingInbound(boolean recordingInbound) {
        this.recordingInbound = recordingInbound;
    }

    /** The other side of the recording — the right channel, where the bot's speech goes on an AI call. */
    public void recordOtherSide(short[] pcm, int length) {
        if (recorder != null && !recorderFailed) {
            recorder.writeBot(pcm, length);
        }
    }

    /**
     * The caller's side of the recording. A write failure — a full disk — used to throw out
     * of the packet's processing before the listeners ran, so STT and VAD lost every packet
     * of every call, with a warning logged fifty times a second each. Now the first failure
     * stops the recording for this call and is logged once; the call itself carries on.
     */
    public void recordCallerSide(short[] pcm, int length) {
        if (recorder == null || recorderFailed) {
            return;
        }
        try {
            recorder.writeCaller(pcm, length);
        } catch (Exception e) {
            recorderFailed = true;
            log.error("Caller recording on port {} failed — not recording the rest of this call: {}",
                    port, e.getMessage());
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
        if (recorder != null) {
            try {
                recorder.close();
            } catch (Exception e) {
                log.warn("Failed to finalize recording on port {}: {}", port, e.getMessage());
            }
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
