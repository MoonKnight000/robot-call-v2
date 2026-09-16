package uz.murodjon.robotcallv2.agent.flow;

import uz.murodjon.robotcallv2.agent.audio.AudioListener;

import java.util.Arrays;

/**
 * Keeps what the caller says, for a voicemail: every sample up to a cap, and when the
 * caller last spoke, so the recording can stop once they have fallen silent.
 */
public final class PcmCapture implements AudioListener {

    private static final int SAMPLE_RATE = 8000;
    /** Frame RMS above which the caller is taken to be speaking — well over line noise. */
    private static final double SPEECH_RMS = 500;

    private final int maxSamples;
    private short[] buffer = new short[SAMPLE_RATE * 10];
    private int size;
    private volatile long lastSpeechAtMillis;
    private volatile boolean heardSpeech;

    public PcmCapture(int maxSeconds) {
        this.maxSamples = maxSeconds * SAMPLE_RATE;
        this.lastSpeechAtMillis = System.currentTimeMillis();
    }

    @Override
    public synchronized void onAudio(short[] pcm, int length) {
        int room = Math.min(length, maxSamples - size);
        if (room <= 0) {
            return;
        }
        if (size + room > buffer.length) {
            buffer = Arrays.copyOf(buffer, Math.min(maxSamples, Math.max(buffer.length * 2, size + room)));
        }
        System.arraycopy(pcm, 0, buffer, size, room);
        size += room;
        double sumSquares = 0;
        for (int i = 0; i < length; i++) {
            sumSquares += (double) pcm[i] * pcm[i];
        }
        if (length > 0 && Math.sqrt(sumSquares / length) > SPEECH_RMS) {
            heardSpeech = true;
            lastSpeechAtMillis = System.currentTimeMillis();
        }
    }

    public synchronized short[] samples() {
        return Arrays.copyOf(buffer, size);
    }

    public synchronized boolean full() {
        return size >= maxSamples;
    }

    public boolean heardSpeech() {
        return heardSpeech;
    }

    public long silentMillis() {
        return System.currentTimeMillis() - lastSpeechAtMillis;
    }

    public synchronized int seconds() {
        return size / SAMPLE_RATE;
    }
}
