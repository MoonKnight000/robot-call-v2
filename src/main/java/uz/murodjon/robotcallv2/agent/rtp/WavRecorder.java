package uz.murodjon.robotcallv2.agent.rtp;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Streams both directions of a call to one 16-bit little-endian stereo WAV file: the
 * caller on the left channel, the bot's own speech on the right.
 *
 * <p>Both sides have to be there. This file is what §11.3 keeps as evidence in a
 * dispute, and a recording holding only the caller's half cannot show what the bot
 * promised. They stay on separate channels rather than being mixed so that a listener
 * — or a later transcript pass — can still tell who said what when both talk at once.
 *
 * <p>The caller's inbound RTP is the clock. It arrives every 20ms for as long as the
 * call is up, while the bot's frames exist only while it is speaking, so each caller
 * frame is written together with whatever bot audio has queued up by then and silence
 * where there is none. That is also why {@link #writeBot} only enqueues: it is called
 * from the Netty event loop, which must never touch the disk (PROJECT.md §7.1, §7.3).
 *
 * <p>A 44-byte placeholder header is reserved up front and rewritten with final sizes
 * on {@link #close()}, so audio is flushed to disk as it arrives without buffering the
 * whole call.
 */
public class WavRecorder implements Closeable {

    private static final int CHANNELS = 2;
    private static final int BYTES_PER_FRAME = CHANNELS * 2;

    /**
     * How much bot audio may wait for caller audio to pair with — ~1s at 20ms frames.
     * Only reached when inbound RTP stalls; the oldest frame is dropped then, so the
     * recording catches back up to the present instead of drifting ever further behind.
     */
    private static final int MAX_PENDING_BOT_FRAMES = 50;

    private final RandomAccessFile raf;
    private final int sampleRate;
    private final RecordingMode mode;
    private final BlockingQueue<short[]> botFrames = new ArrayBlockingQueue<>(MAX_PENDING_BOT_FRAMES);
    private int dataBytes;

    /** The bot frame currently being drawn from, and how far into it we are. */
    private short[] currentBotFrame;
    private int botOffset;

    public WavRecorder(Path path, int sampleRate) throws IOException {
        this(path, sampleRate, RecordingMode.STEREO);
    }

    public WavRecorder(Path path, int sampleRate, RecordingMode mode) throws IOException {
        this.sampleRate = sampleRate;
        this.mode = mode != null ? mode : RecordingMode.STEREO;
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        raf.setLength(0);
        raf.write(new byte[WavHeader.SIZE]);
    }

    /**
     * Append {@code len} samples of the caller's audio, each paired with the bot audio
     * queued behind it. Called from the RTP consumer thread.
     */
    public synchronized void writeCaller(short[] pcm, int len) throws IOException {
        byte[] buf = new byte[len * BYTES_PER_FRAME];
        for (int i = 0; i < len; i++) {
            short caller = pcm[i];
            short bot = nextBotSample();
            short left;
            short right;
            switch (mode) {
                case SPATIAL_STEREO -> {
                    left = clamp16((int) (caller + bot * 0.35));
                    right = clamp16((int) (bot + caller * 0.35));
                }
                case DUAL_MONO -> {
                    short mixed = clamp16((caller + bot) / 2);
                    left = mixed;
                    right = mixed;
                }
                default -> {
                    left = caller;
                    right = bot;
                }
            }
            putLE(buf, i * BYTES_PER_FRAME, left);
            putLE(buf, i * BYTES_PER_FRAME + 2, right);
        }
        raf.write(buf);
        dataBytes += buf.length;
    }

    /**
     * Hand over one frame of the bot's outgoing audio. Copies and queues it — no lock
     * and no I/O, because this runs on the playback pacer's event loop.
     */
    public void writeBot(short[] pcm, int len) {
        short[] frame = Arrays.copyOf(pcm, len);
        if (!botFrames.offer(frame)) {
            botFrames.poll();
            botFrames.offer(frame);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            drainBot();
            raf.seek(0);
            raf.write(WavHeader.bytes(sampleRate, CHANNELS, dataBytes));
        } finally {
            raf.close();
        }
    }

    /** The next bot sample in playback order, or silence while the bot is not speaking. */
    private short nextBotSample() {
        if (currentBotFrame == null || botOffset >= currentBotFrame.length) {
            currentBotFrame = botFrames.poll();
            botOffset = 0;
            if (currentBotFrame == null) {
                return 0;
            }
        }
        return currentBotFrame[botOffset++];
    }

    /**
     * Write out bot audio that never found caller audio to pair with, against silence.
     * This is the tail of the goodbye line: on a call the bot ends, the last of its
     * speech plays after the caller's last inbound packet, and that is exactly the part
     * a dispute turns on.
     */
    private void drainBot() throws IOException {
        int pending = pendingBotSamples();
        if (pending == 0) {
            return;
        }
        byte[] buf = new byte[pending * BYTES_PER_FRAME];
        for (int i = 0; i < pending; i++) {
            short bot = nextBotSample();
            short left;
            short right;
            switch (mode) {
                case SPATIAL_STEREO -> {
                    left = clamp16((int) (bot * 0.35));
                    right = bot;
                }
                case DUAL_MONO -> {
                    short mixed = clamp16(bot / 2);
                    left = mixed;
                    right = mixed;
                }
                default -> {
                    left = 0;
                    right = bot;
                }
            }
            putLE(buf, i * BYTES_PER_FRAME, left);
            putLE(buf, i * BYTES_PER_FRAME + 2, right);
        }
        raf.write(buf);
        dataBytes += buf.length;
    }

    private int pendingBotSamples() {
        int pending = currentBotFrame != null ? currentBotFrame.length - botOffset : 0;
        for (short[] frame : botFrames) {
            pending += frame.length;
        }
        return pending;
    }

    private static short clamp16(int val) {
        if (val > Short.MAX_VALUE) {
            return Short.MAX_VALUE;
        }
        if (val < Short.MIN_VALUE) {
            return Short.MIN_VALUE;
        }
        return (short) val;
    }

    private static void putLE(byte[] buf, int offset, short sample) {
        buf[offset] = (byte) (sample & 0xFF);
        buf[offset + 1] = (byte) ((sample >> 8) & 0xFF);
    }
}
