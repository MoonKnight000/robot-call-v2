package uz.murodjon.uysotvoice.agent.rtp;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Streams 16-bit little-endian mono PCM to a WAV file. A 44-byte placeholder
 * header is reserved up front and rewritten with final sizes on {@link #close()},
 * so audio is flushed to disk as it arrives without buffering the whole call.
 */
public class WavRecorder implements Closeable {

    private final RandomAccessFile raf;
    private final int sampleRate;
    private int dataBytes;

    public WavRecorder(Path path, int sampleRate) throws IOException {
        this.sampleRate = sampleRate;
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        raf.setLength(0);
        raf.write(new byte[WavHeader.SIZE]);
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
            raf.write(WavHeader.bytes(sampleRate, dataBytes));
        } finally {
            raf.close();
        }
    }
}
