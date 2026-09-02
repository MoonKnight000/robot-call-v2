package uz.murodjon.robotcallv2.agent.stt;

/**
 * Receives speech-to-text results as they stream in.
 */
@FunctionalInterface
public interface TranscriptListener {

    /**
     * @param text       recognized text (interim or final)
     * @param isFinal    true for a stable final result, false for an interim hypothesis
     * @param confidence recognition confidence 0..1 (0 when not provided, e.g. interim)
     */
    void onTranscript(String text, boolean isFinal, float confidence);
}
