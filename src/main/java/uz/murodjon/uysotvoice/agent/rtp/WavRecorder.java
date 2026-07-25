package uz.murodjon.uysotvoice.agent.rtp;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Streams 16-bit little-endian mono PCM to a WAV file. A 44-byte placeholder
 * header is reserved up front and rewritten with final sizes on {@link #close()},
 * so audio is flushed to disk as it arrives without buffering the whole call.
 */
public class WavRecorder implements Closeable {

    private static final int HEADER_SIZE = 44;

    private final RandomAccessFile raf;
    private final int sampleRate;
    private int dataBytes;

    public WavRecorder(Path path, int sampleRate) throws IOException {
        this.sampleRate = sampleRate;
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        raf.setLength(0);
        raf.write(new byte[HEADER_SIZE]);
    }

    /** Append {@code len} PCM samples from {@code pcm}. */
    public synchronized void write(short[] pcm, int len) throws IOException {
        byte[] buf = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = pcm[i];
            buf[i * 2] = (byte) (s & 0xFF);
            buf[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        raf.write(buf);
        dataBytes += buf.length;
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            raf.seek(0);
            raf.write(header());
        } finally {
            raf.close();
        }
    }

    private byte[] header() {
        int byteRate = sampleRate * 2; // mono, 16-bit
        byte[] h = new byte[HEADER_SIZE];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, h, 0, 4);
        putIntLE(h, 4, 36 + dataBytes);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, h, 8, 4);
        System.arraycopy("fmt ".getBytes(StandardCharsets.US_ASCII), 0, h, 12, 4);
        putIntLE(h, 16, 16);       // PCM fmt chunk size
        putShortLE(h, 20, 1);      // audio format = PCM
        putShortLE(h, 22, 1);      // channels = mono
        putIntLE(h, 24, sampleRate);
        putIntLE(h, 28, byteRate);
        putShortLE(h, 32, 2);      // block align
        putShortLE(h, 34, 16);     // bits per sample
        System.arraycopy("data".getBytes(StandardCharsets.US_ASCII), 0, h, 36, 4);
        putIntLE(h, 40, dataBytes);
        return h;
    }

    private static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void putShortLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }
}
