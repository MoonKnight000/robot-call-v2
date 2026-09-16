package uz.murodjon.robotcallv2.agent.queue;

/**
 * The built-in hold melody: a slow, quiet arpeggio over four chords (C, Am, F, G), sixteen
 * seconds long so the loop point is not obvious. Synthesized once rather than shipped as a
 * file — nothing to license, nothing to mount, and it is already 8 kHz mono. A company that
 * wants its own music uploads a WAV and points the queue at it.
 */
public final class HoldMusicGenerator {

    static final int SAMPLE_RATE = 8000;
    private static final double CHORD_SECONDS = 4.0;
    private static final double NOTE_SECONDS = 0.5;
    /** Peak of one plucked note — about -23 dBFS, so the music sits well under speech. */
    private static final double NOTE_AMPLITUDE = 2200;
    /** The root held underneath, quieter still. */
    private static final double PAD_AMPLITUDE = 600;
    private static final double[][] CHORDS = {
            {261.63, 329.63, 392.00},
            {220.00, 261.63, 329.63},
            {174.61, 220.00, 261.63},
            {196.00, 246.94, 293.66}
    };
    private static final int[] ARPEGGIO = {0, 1, 2, 1, 0, 1, 2, 1};

    private static volatile short[] melody;

    private HoldMusicGenerator() {
    }

    /** The loop, generated on first use and shared afterwards — callers must not modify it. */
    public static short[] melody() {
        short[] cached = melody;
        if (cached == null) {
            cached = generate();
            melody = cached;
        }
        return cached;
    }

    static short[] generate() {
        int chordSamples = (int) (CHORD_SECONDS * SAMPLE_RATE);
        int noteSamples = (int) (NOTE_SECONDS * SAMPLE_RATE);
        short[] out = new short[chordSamples * CHORDS.length];
        for (int chord = 0; chord < CHORDS.length; chord++) {
            double[] tones = CHORDS[chord];
            int chordStart = chord * chordSamples;
            for (int i = 0; i < chordSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                int note = Math.min(ARPEGGIO.length - 1, i / noteSamples);
                double noteT = (double) (i % noteSamples) / SAMPLE_RATE;
                double frequency = tones[ARPEGGIO[note]];
                double attack = Math.min(1.0, noteT / 0.02);
                double envelope = attack * Math.exp(-3.0 * noteT);
                double pluck = NOTE_AMPLITUDE * envelope
                        * (Math.sin(2 * Math.PI * frequency * t) + 0.25 * Math.sin(4 * Math.PI * frequency * t));
                // The pad fades in and out with each chord so the chord change does not click.
                double chordPhase = (double) i / chordSamples;
                double padEnvelope = Math.sin(Math.PI * chordPhase);
                double pad = PAD_AMPLITUDE * padEnvelope * Math.sin(2 * Math.PI * (tones[0] / 2) * t);
                out[chordStart + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, pluck + pad));
            }
        }
        return out;
    }
}
