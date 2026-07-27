package uz.murodjon.uysotvoice.agent.rtp;

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
import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.codec.G711Codec;

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
 * One RTP UDP listener + sender for a single call, sharing a single NIO datagram
 * socket. Incoming: the Netty handler copies each datagram and enqueues it; a
 * dedicated virtual thread parses RTP, decodes G.711 and records WAV. Outgoing: a
 * single pacer emits one 20ms G.711 frame every 20ms to the peer (symmetric RTP — we
 * reply to the address Asterisk sends from). No blocking I/O runs on the event loop
 * (PROJECT.md §7.1, §7.3, Stages 3–4).
 *
 * <p>Playback is a <em>queue</em>, not a single buffer: sentence-level TTS streaming
 * (§7.2) hands over one sentence at a time while the previous one is still on the
 * wire, and the pacer draws frames across the boundary without a gap. {@link #playPcm}
 * keeps the older "replace whatever is playing" semantics for one-shot playback;
 * {@link #enqueuePcm} appends. {@link #flushPlayback} drops everything — barge-in must
 * silence the whole reply, not just the sentence currently sounding.
 */
public class RtpEndpoint implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RtpEndpoint.class);

    private static final int PT_PCMU = 0; // µ-law
    private static final int PT_PCMA = 8; // A-law
    private static final int QUEUE_CAPACITY = 2000;

    private static final int SAMPLES_PER_FRAME = 160; // 20 ms @ 8 kHz
    private static final int FRAME_MS = 20;
    private static final byte ULAW_SILENCE = (byte) 0xFF;

    private final int port;
    private final WavRecorder recorder;
    private final List<AudioListener> listeners;
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final long ssrc = Integer.toUnsignedLong(new Random().nextInt());

    /** Pending playback audio, oldest first. Guarded by {@link #playLock}. */
    private final Deque<short[]> playQueue = new ArrayDeque<>();
    private final Object playLock = new Object();
    private short[] currentChunk;   // guarded by playLock
    private int currentOffset;      // guarded by playLock

    /**
     * Whether a pacer is (or is about to be) running. Owns the start/stop decision so
     * two threads cannot schedule two pacers for the same socket.
     */
    private final AtomicBoolean pacerRunning = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> pacer;

    private volatile boolean running = true;
    private volatile InetSocketAddress remoteAddress;
    /** True once {@link #remoteAddress} came from an actual inbound packet (symmetric RTP wins). */
    private volatile boolean remoteLatched;
    private io.netty.channel.Channel channel;
    private Thread consumer;

    // Outgoing RTP counters (only touched on the event loop while a playback runs).
    private int sendSeq;
    private long sendTimestamp;

    public RtpEndpoint(int port, WavRecorder recorder, List<AudioListener> listeners) {
        this.port = port;
        this.recorder = recorder;
        this.listeners = List.copyOf(listeners);
    }

    /** Bind the UDP socket and start the consumer. Blocks until bound. */
    public void start(EventLoopGroup group) throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                        if (!remoteLatched) {
                            // Symmetric RTP: the address packets really come from beats
                            // whatever Asterisk advertised (setRemote), so latch on it.
                            InetSocketAddress sender = packet.sender();
                            if (!sender.equals(remoteAddress)) {
                                log.info("RTP peer on port {} latched to {}", port, sender);
                            }
                            remoteAddress = sender;
                            remoteLatched = true;
                        }
                        ByteBuf content = packet.content();
                        byte[] data = new byte[content.readableBytes()];
                        content.readBytes(data);
                        if (!queue.offer(data)) {
                            log.warn("RTP queue full on port {}, dropping packet", port);
                        }
                    }
                });
        channel = bootstrap.bind(port).sync().channel();
        consumer = Thread.ofVirtual().name("rtp-consumer-" + port).start(this::consume);
        log.info("RTP endpoint listening on UDP port {}", port);
    }

    public int port() {
        return port;
    }

    /**
     * Point outgoing RTP at {@code remote} before any packet has arrived from it.
     *
     * <p>Without this we can only discover the peer from its own traffic (symmetric
     * RTP), which fails whenever the caller's audio never reaches Asterisk — e.g. a
     * trunk behind CGNAT: the 2-party bridge has nothing to forward, the
     * externalMedia channel stays silent, and the bot's TTS is dropped with
     * "No RTP peer yet". Asterisk publishes the externalMedia socket it listens on
     * as UNICASTRTP_LOCAL_ADDRESS/PORT, so we can send regardless of the inbound
     * direction. An inbound packet from a different source still wins (see the
     * read handler).
     */
    public void setRemote(InetSocketAddress remote) {
        if (remote == null || remote.getAddress() == null || remote.getPort() <= 0) {
            return;
        }
        if (remoteLatched) {
            return; // real traffic already told us where the peer is
        }
        remoteAddress = remote;
        log.info("RTP peer for port {} set to {} (from Asterisk)", port, remote);
    }

    /**
     * Replace whatever is playing with {@code pcm} (8 kHz mono 16-bit). Used for
     * one-shot playback (a prepared WAV, the manual {@code /say} endpoint) where the
     * new audio is meant to supersede the old.
     */
    public void playPcm(short[] pcm) {
        flushPlayback();
        enqueuePcm(pcm);
    }

    /**
     * Append {@code pcm} (8 kHz mono 16-bit) after whatever is already queued, and
     * start the pacer if it is idle. This is what sentence-level TTS streaming uses:
     * each sentence is handed over as it is synthesized and plays back-to-back with
     * the previous one.
     *
     * <p>Returns as soon as the audio is queued; playback continues asynchronously.
     */
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
        }
        startPacer();
    }

    /**
     * Immediately stop playback and drop everything still queued (barge-in —
     * PROJECT.md §7.2). Safe to call when nothing is playing.
     */
    public void flushPlayback() {
        synchronized (playLock) {
            playQueue.clear();
            currentChunk = null;
            currentOffset = 0;
        }
        stopPacer();
    }

    /** Whether audio is currently being streamed to the peer. */
    public boolean isPlaying() {
        return pacerRunning.get();
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

    /**
     * Start the 20ms pacer if it is not already running.
     *
     * <p>The schedule call itself is posted to the event loop rather than made from
     * the calling thread. The event loop runs its tasks one at a time, so the {@link
     * #pacer} field is assigned before {@link #sendFrame} can first run — scheduling
     * from here would race, and a first frame that finished the queue immediately
     * would leave a cancelled-but-recorded future behind, pinning {@code isPlaying()}
     * to true for the rest of the call.
     */
    private void startPacer() {
        if (!running || channel == null || !pacerRunning.compareAndSet(false, true)) {
            return;
        }
        channel.eventLoop().execute(() ->
                pacer = channel.eventLoop().scheduleAtFixedRate(
                        this::sendFrame, 0, FRAME_MS, TimeUnit.MILLISECONDS));
    }

    private void stopPacer() {
        pacerRunning.set(false);
        ScheduledFuture<?> current = pacer;
        if (current != null) {
            current.cancel(false);
            pacer = null;
        }
    }

    /**
     * Emit one 20ms frame, drawing samples across queued chunk boundaries so a
     * sentence break costs no audible gap. Stops the pacer once the queue runs dry.
     * Runs on the event loop.
     */
    private void sendFrame() {
        InetSocketAddress remote = remoteAddress;
        if (!running || remote == null) {
            stopPacer();
            return;
        }
        byte[] ulaw = new byte[SAMPLES_PER_FRAME];
        int filled = 0;
        boolean first;
        synchronized (playLock) {
            while (filled < SAMPLES_PER_FRAME) {
                if (currentChunk == null || currentOffset >= currentChunk.length) {
                    currentChunk = playQueue.pollFirst();
                    currentOffset = 0;
                    if (currentChunk == null) {
                        break; // nothing more queued
                    }
                }
                int n = Math.min(SAMPLES_PER_FRAME - filled, currentChunk.length - currentOffset);
                for (int i = 0; i < n; i++) {
                    ulaw[filled + i] = G711Codec.pcmToUlaw(currentChunk[currentOffset + i]);
                }
                filled += n;
                currentOffset += n;
            }
        }
        if (filled == 0) {
            stopPacer();
            return;
        }
        // A short tail is padded to a whole frame; Asterisk expects fixed-size frames.
        for (int i = filled; i < SAMPLES_PER_FRAME; i++) {
            ulaw[i] = ULAW_SILENCE;
        }
        first = sendSeq == 0;
        try {
            byte[] rtp = RtpPacket.toBytes(PT_PCMU, sendSeq & 0xFFFF, sendTimestamp, ssrc, first, ulaw);
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
        while (running || !queue.isEmpty()) {
            byte[] raw;
            try {
                raw = queue.poll(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (raw == null) {
                continue;
            }
            try {
                RtpPacket p = RtpPacket.parse(raw, raw.length);
                byte[] payload = p.payload();
                if (payload.length > pcm.length) {
                    pcm = new short[payload.length];
                }
                switch (p.payloadType()) {
                    case PT_PCMU -> G711Codec.ulawToPcm(payload, payload.length, pcm);
                    case PT_PCMA -> G711Codec.alawToPcm(payload, payload.length, pcm);
                    default -> {
                        // Non-audio (e.g. comfort noise / DTMF) — skip.
                        continue;
                    }
                }
                recorder.write(pcm, payload.length);
                for (AudioListener listener : listeners) {
                    listener.onAudio(pcm, payload.length);
                }
            } catch (Exception e) {
                log.warn("RTP processing error on port {}: {}", port, e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
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
}
