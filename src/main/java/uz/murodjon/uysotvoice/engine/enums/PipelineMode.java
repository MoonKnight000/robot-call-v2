package uz.murodjon.uysotvoice.engine.enums;

/**
 * How a company's calls turn speech into speech (PROJECT.md §2.4/§2.5).
 *
 * <p>The two modes are not two settings of one pipeline — they are different pipelines,
 * which is why the choice is an enum rather than another provider id. {@link #CASCADE}
 * runs the chain this project was built on; {@link #REALTIME} hands the whole chain to a
 * single speech-to-speech engine.
 */
public enum PipelineMode {

    /** STT → LLM → TTS, each a separately selected provider. */
    CASCADE,

    /**
     * One speech-to-speech engine (Gemini Live and the like) does recognition, reasoning
     * and speech itself: audio in, audio out, with its own endpointing and barge-in.
     */
    REALTIME
}
