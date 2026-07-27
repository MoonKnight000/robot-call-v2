package uz.murodjon.uysotvoice.agent.audio;

/**
 * Decides which of a call's audio is worth sending to the speech recognizer.
 *
 * <p>Yandex bills STT per streamed audio-second, and a debt-collection call is mostly
 * not speech: the bot talks, the caller thinks, the line sits idle. Streaming all of it
 * paid full price for silence. The VAD that already runs for barge-in scores every
 * window anyway, so the same signal opens and closes a gate in front of the STT stream.
 *
 * <p>Two margins keep the recognizer whole. A <b>pre-roll</b> ring buffer holds the last
 * few hundred milliseconds while the gate is shut, so the syllables that arrive before
 * the VAD has made up its mind are still delivered — without it every utterance loses
 * its first word. A <b>post-roll</b> hangover keeps the stream open through the pause
 * after speech, because the provider's end-of-utterance detector needs to hear that
 * silence to emit a final; cutting the audio the moment speech stops would leave the
 * turn hanging with no final at all.
 *
 * <p>Not thread-safe, and does not need to be: one gate belongs to one call and every
 * method is called from that call's single RTP consumer thread.
 */
public class SpeechGate {

    private final int hangoverSamples;
    private final short[] preRoll;

    /** Write position in the ring, and how many samples it currently holds. */
    private int writePos;
    private int filled;

    private boolean open;
    private int silenceSamples;

    /** Set when VAD is unusable — the gate then passes everything, as before gating. */
    private boolean bypassed;

    public SpeechGate(int sampleRate, int preRollMs, int postRollMs) {
        this.hangoverSamples = Math.max(0, postRollMs) * sampleRate / 1000;
        this.preRoll = new short[Math.max(1, Math.max(0, preRollMs) * sampleRate / 1000)];
    }

    /**
     * One scored VAD window. Speech opens the gate immediately (the pre-roll covers
     * what came before); silence closes it only after the post-roll hangover.
     *
     * @param speech  whether the window scored above the VAD threshold
     * @param samples how many samples the window covered
     */
    public void onVadWindow(boolean speech, int samples) {
        if (bypassed) {
            return;
        }
        if (speech) {
            silenceSamples = 0;
            open = true;
            return;
        }
        if (!open) {
            return;
        }
        silenceSamples += samples;
        if (silenceSamples >= hangoverSamples) {
            open = false;
            silenceSamples = 0;
        }
    }

    /**
     * Stop gating for the rest of the call — used when VAD inference fails. Recognition
     * quality is never traded for the saving: no VAD means no gate.
     */
    public void bypass() {
        bypassed = true;
        open = true;
    }

    public boolean isOpen() {
        return open;
    }

    /** Hold audio that arrived while the gate was shut, oldest samples dropped first. */
    public void buffer(short[] pcm, int length) {
        int count = Math.min(length, preRoll.length);
        // Only the tail can survive in the ring, so skip straight to it.
        int start = length - count;
        for (int i = 0; i < count; i++) {
            preRoll[writePos] = pcm[start + i];
            writePos = (writePos + 1) % preRoll.length;
        }
        filled = Math.min(filled + count, preRoll.length);
    }

    /**
     * Take everything buffered while the gate was shut, oldest first, and empty the
     * ring. Returns {@code null} when there is nothing held.
     */
    public short[] drainPreRoll() {
        if (filled == 0) {
            return null;
        }
        short[] out = new short[filled];
        int start = (writePos - filled + preRoll.length) % preRoll.length;
        for (int i = 0; i < filled; i++) {
            out[i] = preRoll[(start + i) % preRoll.length];
        }
        filled = 0;
        writePos = 0;
        return out;
    }
}
