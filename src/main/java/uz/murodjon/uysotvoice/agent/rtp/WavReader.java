package uz.murodjon.uysotvoice.agent.rtp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal RIFF/WAVE reader for PCM audio. Returns 16-bit mono samples; stereo
 * input is down-mixed to the left channel. Resampling is not done here — for
 * Stage 4 playback the file should already be 8 kHz mono (telephone rate).
 */
public final class WavReader {

    private WavReader() {
    }

    public static WavAudio read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Parse RIFF/WAVE bytes already in memory (e.g. a TTS provider's WAV output).
     */
    public static WavAudio read(byte[] bytes) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        if (bytes.length < 12 || buf.getInt(0) != 0x46464952 /* "RIFF" */) {
            throw new IOException("Not a RIFF/WAVE file");
        }

        int sampleRate = 8000;
        int channels = 1;
        int bitsPerSample = 16;
        int dataOffset = -1;
        int dataLength = 0;

        int pos = 12; // skip RIFF header + "WAVE"
        while (pos + 8 <= bytes.length) {
            int chunkId = buf.getInt(pos);
            int chunkSize = buf.getInt(pos + 4);
            int body = pos + 8;
            if (chunkId == 0x20746d66 /* "fmt " */) {
                channels = buf.getShort(body + 2) & 0xFFFF;
                sampleRate = buf.getInt(body + 4);
                bitsPerSample = buf.getShort(body + 14) & 0xFFFF;
            } else if (chunkId == 0x61746164 /* "data" */) {
                dataOffset = body;
                dataLength = chunkSize;
                break;
            }
            pos = body + chunkSize + (chunkSize & 1); // chunks are word-aligned
        }

        if (dataOffset < 0) {
            throw new IOException("No data chunk in WAV");
        }
        if (bitsPerSample != 16) {
            throw new IOException("Only 16-bit PCM supported, got " + bitsPerSample + "-bit");
        }
        dataLength = Math.min(dataLength, bytes.length - dataOffset);

        int frames = dataLength / (2 * channels);
        short[] samples = new short[frames];
        for (int i = 0; i < frames; i++) {
            // Left channel only when stereo.
            samples[i] = buf.getShort(dataOffset + i * 2 * channels);
        }
        return new WavAudio(sampleRate, samples);
    }
}
