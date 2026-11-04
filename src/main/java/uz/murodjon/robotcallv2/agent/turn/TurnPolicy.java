package uz.murodjon.robotcallv2.agent.turn;

/**
 * How a call combines its end-of-turn verdicts. The speech gate measures the silence and asks;
 * the policy only weighs what the sources said — it holds no state and no timers.
 *
 * <p>Every argument may be {@link TurnCue#UNKNOWN}: a source that is not installed, has not
 * judged yet, or judged words the caller has since added to.
 */
public interface TurnPolicy {

    /** The policy's name for close reasons, metrics and the turn dataset. */
    String name();

    /**
     * What the caller's words say: {@link TurnCue#COMPLETE} lets a finished sentence close on the
     * shorter wait, {@link TurnCue#INCOMPLETE} / {@link TurnCue#WAIT} keep a short run and an early
     * close from cutting the caller off.
     *
     * @param llm   the language model's verdict on the latest interim
     * @param words the word rules' verdict on it
     */
    TurnCue readWords(TurnCue llm, TurnCue words);

    /**
     * Whether the words earn one of the short waits (a short run, a few words answering a closed
     * question, a finished-sounding sentence) instead of the full hangover. A positive verdict,
     * not the absence of an objection: on recorded uz-UZ calls the 350–400 ms waits granted on
     * "nobody said unfinished" cut the caller off in 45% of closes, against 17% for the longer
     * ones — the language model's verdict arrives ~600 ms after the interim, after the short
     * wait had already closed the turn.
     *
     * @param llm   the language model's verdict on the latest interim
     * @param words the word rules' verdict on it
     */
    boolean allowsShortWait(TurnCue llm, TurnCue words);

    /**
     * The call on a pause whose wait has run out.
     *
     * @param llm       the language model's verdict on the latest interim
     * @param turnModel the turn model's verdict on this pause ({@link TurnOpinion#cue()})
     * @param words     the word rules' verdict on the latest interim
     */
    TurnCall judgePause(TurnCue llm, TurnCue turnModel, TurnCue words);
}
