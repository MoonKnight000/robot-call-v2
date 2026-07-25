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
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One RTP UDP listener + sender for a single call, sharing a single NIO datagram
 * socket. Incoming: the Netty handler copies each datagram and enqueues it; a
 * dedicated virtual thread parses RTP, decodes G.711 and records WAV. Outgoing:
 * {@link #playPcm} paces one 20ms G.711 frame every 20ms back to the peer
 * (symmetric RTP — we reply to the address Asterisk sends from).
 * No blocking I/O runs on the event loop (PROJECT.md §7.1, §7.3, Stages 3–4).
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
    private final AtomicReference<ScheduledFuture<?>> playback = new AtomicReference<>();

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
     * Stream {@code pcm} (8 kHz mono 16-bit) to the peer as µ-law RTP, one 20ms
     * frame every 20ms. Cancels any playback already in progress. Returns once
     * scheduling has started (playback continues asynchronously).
     */
    public void playPcm(short[] pcm) {
        InetSocketAddress remote = awaitRemote();
        if (remote == null) {
            log.warn("No RTP peer on port {} yet; cannot play {} samples", port, pcm.length);
            return;
        }
        stopPlayback();

        int totalFrames = (pcm.length + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME;
        int[] frameIndex = {0};
        ScheduledFuture<?> future = channel.eventLoop().scheduleAtFixedRate(() -> {
            int i = frameIndex[0];
            if (!running || i >= totalFrames) {
                stopPlayback();
                return;
            }
            try {
                int start = i * SAMPLES_PER_FRAME;
                int len = Math.min(SAMPLES_PER_FRAME, pcm.length - start);
                byte[] ulaw = new byte[SAMPLES_PER_FRAME];
                for (int s = 0; s < SAMPLES_PER_FRAME; s++) {
                    ulaw[s] = (s < len) ? G711Codec.pcmToUlaw(pcm[start + s]) : ULAW_SILENCE;
                }
                byte[] rtp = RtpPacket.toBytes(PT_PCMU, sendSeq & 0xFFFF, sendTimestamp, ssrc, i == 0, ulaw);
                channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(rtp), remote));
                sendSeq++;
                sendTimestamp += SAMPLES_PER_FRAME;
                frameIndex[0]++;
            } catch (Exception e) {
                log.warn("RTP send error on port {}: {}", port, e.getMessage());
                stopPlayback();
            }
        }, 0, FRAME_MS, TimeUnit.MILLISECONDS);
        playback.set(future);
        log.info("Playing {} frames ({} ms) to {} on port {}", totalFrames, totalFrames * FRAME_MS, remote, port);
    }

    /**
     * Immediately stop any playback in progress and drop its remaining frames
     * (barge-in — PROJECT.md §7.2). Safe to call when nothing is playing.
     */
    public void flushPlayback() {
        stopPlayback();
    }

    /** Whether a TTS playback is currently streaming to the peer. */
    public boolean isPlaying() {
        return playback.get() != null;
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

    private void stopPlayback() {
        ScheduledFuture<?> future = playback.getAndSet(null);
        if (future != null) {
            future.cancel(false);
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
        stopPlayback();
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
