package uz.murodjon.robotcallv2.agent.vad;

import java.io.Closeable;

/**
 * One call's voice-activity model: scores consecutive windows of that call's audio and keeps
 * whatever state the model carries between them. Opened per stream ({@link SileroVad#openStream},
 * {@link TenVad#openStream}) and used from the one thread that reads the call's audio.
 */
@FunctionalInterface
public interface VadScorer extends Closeable {

    /**
     * @param window {@code windowSamples} float samples in [-1, 1], at the VAD sample rate
     * @return speech probability, or {@code -1} on inference error
     */
    float score(float[] window);

    /** The barge-in threshold this model's scores need, or NaN for {@code vad.threshold}. */
    default float bargeInThreshold() {
        return Float.NaN;
    }

    /** The listening threshold this model's scores need, or NaN for {@code vad.listening-threshold}. */
    default float listeningThreshold() {
        return Float.NaN;
    }

    /** The model's name for metrics and the turn dataset: {@code silero}, {@code ten}. */
    default String engine() {
        return "unknown";
    }

    /** Releases what the model holds for this stream; nothing by default. */
    @Override
    default void close() {
    }
}
