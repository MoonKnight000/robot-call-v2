package uz.murodjon.robotcallv2.agent.turn;

/**
 * Where an end-of-turn verdict comes from, as a {@link TurnPolicy} weighs it.
 */
public enum TurnSource {

    /** A language model: the LLM turn detector or the speculative reply's cue. */
    LLM,

    /** The active {@link TurnSignal} — Smart Turn from the audio, or the text model. */
    TURN_MODEL,

    /** The Uzbek/Russian word rules on the last interim ({@link TranscriptTurnCues}). */
    WORDS;

    /** The source's name in close reasons, metrics and the turn dataset: {@code llm}, {@code turn-model}, {@code words}. */
    public String label() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
