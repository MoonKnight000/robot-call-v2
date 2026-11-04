package uz.murodjon.robotcallv2.agent.turn;

/**
 * Every source, combined as the speech gate always has ({@link TurnEngine#AUTO}):
 * <ul>
 *   <li>the words: the language model's verdict when it has one, the word rules only when it
 *       has none — the rules see one last word, the model the sentence and the agent's question;</li>
 *   <li>the pause: a model asking for time or calling it unfinished extends it; otherwise an
 *       unfinished turn model, or unfinished words, extend it; otherwise it closes;</li>
 *   <li>a short wait: only on the language model's "finished".</li>
 * </ul>
 */
public final class AutoTurnPolicy implements TurnPolicy {

    public static final AutoTurnPolicy INSTANCE = new AutoTurnPolicy();

    private AutoTurnPolicy() {
    }

    @Override
    public String name() {
        return "auto";
    }

    @Override
    public TurnCue readWords(TurnCue llm, TurnCue words) {
        return llm != TurnCue.UNKNOWN ? llm : words;
    }

    /** Only the language model's "finished": the word rules alone cut callers off (see {@link TurnPolicy}). */
    @Override
    public boolean allowsShortWait(TurnCue llm, TurnCue words) {
        return llm == TurnCue.COMPLETE;
    }

    @Override
    public TurnCall judgePause(TurnCue llm, TurnCue turnModel, TurnCue words) {
        if (llm == TurnCue.WAIT || llm == TurnCue.INCOMPLETE) {
            return new TurnCall(llm, TurnSource.LLM);
        }
        if (turnModel == TurnCue.INCOMPLETE) {
            return new TurnCall(TurnCue.INCOMPLETE, TurnSource.TURN_MODEL);
        }
        if (llm == TurnCue.UNKNOWN && words == TurnCue.INCOMPLETE) {
            return new TurnCall(TurnCue.INCOMPLETE, TurnSource.WORDS);
        }
        return TurnCall.CLOSE;
    }
}
