package uz.murodjon.robotcallv2.agent.rtp;

import java.nio.charset.StandardCharsets;

/**
 * The 44-byte PCM WAV header layout, shared by {@link WavRecorder} (a real,
 * known-length recording) and the live "listen in" stream (§10.3), whose length is
 * never known up front because it ends whenever the operator disconnects.
 */
public final class WavHeader {

    public static final int SIZE = 44;

    private WavHeader() {
    }

    /**
     * 16-bit PCM header for {@code dataBytes} of audio at {@code sampleRate}.
     *
     * @param channels 1 for the live "listen in" stream (both directions already mixed
     *                 into one), 2 for a recording that keeps them apart
     */
    public static byte[] bytes(int sampleRate, int channels, int dataBytes) {
        int blockAlign = channels * 2; // 16-bit samples
        int byteRate = sampleRate * blockAlign;
        byte[] h = new byte[SIZE];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, h, 0, 4);
        putIntLE(h, 4, 36 + dataBytes);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, h, 8, 4);
        System.arraycopy("fmt ".getBytes(StandardCharsets.US_ASCII), 0, h, 12, 4);
        putIntLE(h, 16, 16);       // PCM fmt chunk size
        putShortLE(h, 20, 1);      // audio format = PCM
        putShortLE(h, 22, channels);
        putIntLE(h, 24, sampleRate);
        putIntLE(h, 28, byteRate);
        putShortLE(h, 32, blockAlign);
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
