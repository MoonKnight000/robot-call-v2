package uz.murodjon.robotcallv2.agent.turn;

import java.util.List;

/**
 * The sources in a trusted order: the first one with a verdict decides, and those after it are
 * not asked ({@link TurnEngine#MIXED}; {@link TurnEngine#LLM}, {@link TurnEngine#TURN_MODEL} and
 * {@link TurnEngine#WORDS} are the one-source orders).
 *
 * <p>Unlike {@link AutoTurnPolicy}, a trusted "finished" is not overruled by a less trusted
 * "unfinished" further down — that is the point of choosing an order: on a recorded call the
 * word rules called "… odam edimda" unfinished while the model and Smart Turn said finished, and
 * the caller waited 12 s.
 */
public final class PriorityTurnPolicy implements TurnPolicy {

    private final String name;
    private final List<TurnSource> order;

    public PriorityTurnPolicy(String name, List<TurnSource> order) {
        this.name = name;
        this.order = List.copyOf(order);
    }

    @Override
    public String name() {
        return name;
    }

    /** The order the sources are trusted in. */
    public List<TurnSource> order() {
        return order;
    }

    @Override
    public TurnCue readWords(TurnCue llm, TurnCue words) {
        for (TurnSource source : order) {
            TurnCue cue = switch (source) {
                case LLM -> llm;
                case WORDS -> words;
                case TURN_MODEL -> TurnCue.UNKNOWN; // hears the audio, says nothing about the words
            };
            if (cue != TurnCue.UNKNOWN) {
                return cue;
            }
        }
        return TurnCue.UNKNOWN;
    }

    /**
     * The trusted order's reading of the words must be "finished", and it must be the language
     * model's — unless the order trusts the word rules alone ({@link TurnEngine#WORDS}), which is
     * choosing them on purpose.
     */
    @Override
    public boolean allowsShortWait(TurnCue llm, TurnCue words) {
        if (readWords(llm, words) != TurnCue.COMPLETE) {
            return false;
        }
        return llm == TurnCue.COMPLETE || order.equals(List.of(TurnSource.WORDS));
    }

    @Override
    public TurnCall judgePause(TurnCue llm, TurnCue turnModel, TurnCue words) {
        for (TurnSource source : order) {
            TurnCue cue = switch (source) {
                case LLM -> llm;
                case TURN_MODEL -> turnModel;
                case WORDS -> words;
            };
            if (cue != TurnCue.UNKNOWN) {
                return new TurnCall(cue, source);
            }
        }
        return TurnCall.CLOSE;
    }
}
