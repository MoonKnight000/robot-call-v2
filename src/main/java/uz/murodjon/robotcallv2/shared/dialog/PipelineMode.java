package uz.murodjon.robotcallv2.shared.dialog;

/**
 * How a company's calls turn speech into speech (PROJECT.md §2.4/§2.5).
 */
public enum PipelineMode {
    /** Our STT, our LLM, our TTS — every sentence is checked before it is spoken. */
    CASCADE,
    /** A speech-to-speech engine hears, thinks and speaks; its words are only audited after the fact. */
    REALTIME,
    /**
     * Our STT hears the caller and hands each utterance to a speech-to-speech engine as text;
     * the engine thinks and speaks in its own voice. The engine's own recognizer wrote Uzbek
     * callers down as Russian or Turkish and answered what it misheard.
     */
    HALF_CASCADE;

    /** Whether a speech-to-speech engine runs the conversation (REALTIME or HALF_CASCADE). */
    public boolean usesRealtimeEngine() {
        return this != CASCADE;
    }

    /** Whether the caller hears our TTS voice rather than the engine's own. */
    public boolean speaksWithTts() {
        return this == CASCADE;
    }
}
