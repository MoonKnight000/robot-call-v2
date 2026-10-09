package uz.murodjon.robotcallv2.agent.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Says when a voicemail has started recording, so the message the agent leaves on it is
 * heard from its first word rather than spoken over the greeting.
 *
 * <p>The cue is the beep: a pure tone, which speech never is. Each 20 ms frame is
 * measured with the Goertzel algorithm at every 25 Hz between 400 and 2400 Hz, and a frame
 * whose energy sits almost entirely in one of those bins is tonal. A run of tonal frames
 * at the same pitch is a beep. A vowel concentrates its energy too, but across a
 * fundamental and its harmonics, so no single bin holds most of it.
 *
 * <p>Not every recorder beeps, so two fallbacks start the message anyway: the line going
 * quiet for {@code silenceMs} (the greeting is over and the box is listening), and
 * {@code maxWaitMs} after arming whatever was heard — a message spoken late is still a
 * message, a call held open waiting for a beep that never comes is not.
 *
 * <p>Runs on the RTP consumer thread; {@code onReady} is called there once and must hand
 * any real work off.
 */
public class VoicemailBeepDetector implements AudioListener {

    private static final Logger log = LoggerFactory.getLogger(VoicemailBeepDetector.class);

    private static final int FRAME_MS = 20;
    private static final int MIN_HZ = 400;
    private static final int MAX_HZ = 2400;
    /** Half the 50 Hz resolution of a 20 ms frame, so a tone between two bins still lands mostly in one. */
    private static final int STEP_HZ = 25;
    /** Share of a frame's energy one bin must hold for the frame to count as a tone. */
    private static final double TONAL_RATIO = 0.6;
    /** How far the pitch may wander between frames of one beep. */
    private static final int PITCH_TOLERANCE_HZ = 50;
    /** Beeps run 300 ms to a second; half the shortest leaves room for a frame lost at either end. */
    private static final int MIN_BEEP_MS = 160;
    /** Below this RMS a frame is line noise, neither a tone nor speech. */
    private static final double SILENCE_RMS = 200;

    private final String channelId;
    private final int frameSamples;
    private final int minBeepFrames;
    private final int silenceFrames;
    private final int maxWaitFrames;
    private final double[] coefficients;
    private final Runnable onReady;

    private final double[] frame;
    private int filled;
    private int frames;
    private int quietFrames;
    private int toneFrames;
    private int toneHz;
    private boolean finished;

    /**
     * @param sampleRate rate of the PCM this listener is fed
     * @param silenceMs  continuous quiet after which the box is taken to be recording
     * @param maxWaitMs  how long to wait for either cue before starting the message anyway
     * @param onReady    called once, when the message may start
     */
    public VoicemailBeepDetector(String channelId, int sampleRate, int silenceMs, int maxWaitMs, Runnable onReady) {
        this.channelId = channelId;
        this.frameSamples = sampleRate * FRAME_MS / 1000;
        this.minBeepFrames = MIN_BEEP_MS / FRAME_MS;
        this.silenceFrames = Math.max(1, silenceMs / FRAME_MS);
        this.maxWaitFrames = Math.max(1, maxWaitMs / FRAME_MS);
        this.onReady = onReady;
        this.frame = new double[frameSamples];
        this.coefficients = new double[(MAX_HZ - MIN_HZ) / STEP_HZ + 1];
        for (int i = 0; i < coefficients.length; i++) {
            coefficients[i] = 2 * Math.cos(2 * Math.PI * (MIN_HZ + i * STEP_HZ) / sampleRate);
        }
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        for (int i = 0; i < length && !finished; i++) {
            frame[filled++] = pcm[i];
            if (filled == frameSamples) {
                filled = 0;
                process();
            }
        }
    }

    private void process() {
        frames++;
        double energy = 0;
        for (double sample : frame) {
            energy += sample * sample;
        }
        if (Math.sqrt(energy / frameSamples) < SILENCE_RMS) {
            toneFrames = 0;
            if (++quietFrames >= silenceFrames) {
                ready("silence");
                return;
            }
        } else {
            quietFrames = 0;
            int hz = tonePitch(energy);
            if (hz > 0 && toneFrames > 0 && Math.abs(hz - toneHz) <= PITCH_TOLERANCE_HZ) {
                toneFrames++;
            } else {
                toneFrames = hz > 0 ? 1 : 0;
            }
            toneHz = hz;
            if (toneFrames >= minBeepFrames) {
                ready("beep at " + hz + " Hz");
                return;
            }
        }
        if (frames >= maxWaitFrames) {
            ready("no beep or silence in " + frames * FRAME_MS + " ms");
        }
    }

    /** The bin holding most of the frame's energy, or 0 when no bin holds enough to be a tone. */
    private int tonePitch(double energy) {
        // A full-scale sine of amplitude A puts (A·N/2)² into its bin and has energy A²·N/2,
        // so power / (N·energy/2) is the share of the frame in that bin: 1 for a pure tone.
        double norm = frameSamples * energy / 2;
        double best = 0;
        int bestIndex = -1;
        for (int i = 0; i < coefficients.length; i++) {
            double coefficient = coefficients[i];
            double previous = 0;
            double beforePrevious = 0;
            for (double sample : frame) {
                double current = sample + coefficient * previous - beforePrevious;
                beforePrevious = previous;
                previous = current;
            }
            double power = previous * previous + beforePrevious * beforePrevious
                    - coefficient * previous * beforePrevious;
            if (power > best) {
                best = power;
                bestIndex = i;
            }
        }
        return bestIndex >= 0 && best / norm >= TONAL_RATIO ? MIN_HZ + bestIndex * STEP_HZ : 0;
    }

    private void ready(String cue) {
        finished = true;
        log.info("[{}] voicemail is recording ({}) — leaving the message", channelId, cue);
        try {
            onReady.run();
        } catch (Exception e) {
            log.warn("[{}] voicemail message handler failed: {}", channelId, e.getMessage());
        }
    }
}
