package uz.murodjon.robotcallv2.agent.vad;

import java.util.List;

/**
 * Two (or more) voice-activity models scoring the same window, combined into one score
 * ({@link VadEngine#MIXED}).
 *
 * <p>The models do not share a scale — Silero calls speech from 0.4, TEN from 0.6 — so each
 * score is first mapped onto a common one where the model's own barge-in threshold sits at
 * {@link #THRESHOLD}. Then:
 * <ul>
 *   <li><b>onset</b> (the last window was not speech): the lowest mapped score — speech starts
 *       only when every model hears it;</li>
 *   <li><b>hold</b> (the last window was speech): the highest — speech goes on while any model
 *       still hears it.</li>
 * </ul>
 * A model that fails on a window ({@code -1}) is left out of that window; all failing is a failure.
 *
 * <p>Used from the one thread that reads the call's audio, like every {@link VadScorer}.
 */
public final class MixedVadScorer implements VadScorer {

    /** Where every model's barge-in threshold lands on the common scale. */
    static final float THRESHOLD = 0.5f;

    private final List<VadScorer> scorers;
    private final float[] bargeInThresholds;
    private final float listeningThreshold;
    private boolean speaking;

    /**
     * @param scorers            the models, each already opened for this call
     * @param defaultThreshold   {@code vad.threshold}, for a model that names no threshold of its own
     * @param defaultListening   {@code vad.listening-threshold}, likewise
     */
    public MixedVadScorer(List<VadScorer> scorers, float defaultThreshold, float defaultListening) {
        this.scorers = List.copyOf(scorers);
        this.bargeInThresholds = new float[this.scorers.size()];
        float listening = THRESHOLD;
        for (int i = 0; i < this.scorers.size(); i++) {
            VadScorer scorer = this.scorers.get(i);
            float bargeIn = Float.isNaN(scorer.bargeInThreshold()) ? defaultThreshold : scorer.bargeInThreshold();
            float listen = Float.isNaN(scorer.listeningThreshold()) ? defaultListening : scorer.listeningThreshold();
            bargeInThresholds[i] = bargeIn;
            // The most sensitive model's listening line, carried onto the common scale: listening
            // exists to catch quiet speech, and onset already asks every model to agree.
            listening = Math.min(listening, toCommonScale(listen, bargeIn));
        }
        this.listeningThreshold = listening;
    }

    @Override
    public float score(float[] window) {
        float lowest = Float.MAX_VALUE;
        float highest = -1f;
        for (int i = 0; i < scorers.size(); i++) {
            float raw = scorers.get(i).score(window);
            if (raw < 0f) {
                continue;
            }
            float mapped = toCommonScale(raw, bargeInThresholds[i]);
            lowest = Math.min(lowest, mapped);
            highest = Math.max(highest, mapped);
        }
        if (highest < 0f) {
            return -1f;
        }
        float combined = speaking ? highest : lowest;
        speaking = combined >= listeningThreshold;
        return combined;
    }

    /**
     * Piecewise-linear: 0 → 0, the model's threshold → {@link #THRESHOLD}, 1 → 1. Monotone, so
     * "above its own threshold" and "above the common one" are the same statement.
     */
    static float toCommonScale(float score, float threshold) {
        if (threshold <= 0f || threshold >= 1f) {
            return score;
        }
        return score <= threshold
                ? score / threshold * THRESHOLD
                : THRESHOLD + (score - threshold) / (1f - threshold) * (1f - THRESHOLD);
    }

    @Override
    public float bargeInThreshold() {
        return THRESHOLD;
    }

    @Override
    public float listeningThreshold() {
        return listeningThreshold;
    }

    @Override
    public String engine() {
        StringBuilder name = new StringBuilder("mixed");
        for (VadScorer scorer : scorers) {
            name.append('-').append(scorer.engine());
        }
        return name.toString();
    }

    @Override
    public void close() {
        for (VadScorer scorer : scorers) {
            scorer.close();
        }
    }
}
