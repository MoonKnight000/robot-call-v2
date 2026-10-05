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
     * A speech-to-speech engine hears and thinks, but its own voice is dropped: the text of
     * its answer goes through the fact guard and is spoken by our TTS voice. Keeps the
     * engine's fast understanding of the caller's audio while every figure is still gated,
     * and the brand voice stays the same as on CASCADE calls.
     */
    HALF_CASCADE;

    /** Whether a speech-to-speech engine runs the conversation (REALTIME or HALF_CASCADE). */
    public boolean usesRealtimeEngine() {
        return this != CASCADE;
    }

    /** Whether the caller hears our TTS voice rather than the engine's own. */
    public boolean speaksWithTts() {
        return this != REALTIME;
    }
}
