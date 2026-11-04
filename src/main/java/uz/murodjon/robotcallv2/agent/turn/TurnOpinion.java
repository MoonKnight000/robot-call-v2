package uz.murodjon.robotcallv2.agent.turn;

/**
 * One {@link TurnSignal}'s view of a pause.
 *
 * @param cue         {@link TurnCue#COMPLETE}, {@link TurnCue#INCOMPLETE}, or {@link TurnCue#UNKNOWN}
 *                    when it could not judge (no model, no text, failed inference)
 * @param probability p(the caller has finished), or {@code -1} when it was not scored
 * @param confident   finished beyond the doubt it takes to close before the wait has run out;
 *                    never true for an unknown opinion
 */
public record TurnOpinion(TurnCue cue, float probability, boolean confident) {

    /** No opinion: the wait stays as the timer set it, and nothing closes early. */
    public static final TurnOpinion UNKNOWN = new TurnOpinion(TurnCue.UNKNOWN, -1f, false);

    /**
     * The opinion a scored model gives: finished at {@code threshold}, sure of it at
     * {@code earlyThreshold}; a probability below 0 is no opinion.
     */
    public static TurnOpinion fromProbability(float probability, float threshold, float earlyThreshold) {
        if (probability < 0f) {
            return UNKNOWN;
        }
        return new TurnOpinion(probability >= threshold ? TurnCue.COMPLETE : TurnCue.INCOMPLETE,
                probability, probability >= earlyThreshold);
    }

    /**
     * Whether the pause may close when the wait runs out. Errs towards yes: an unknown opinion
     * leaves the timer's wait alone rather than holding every caller for the extension.
     */
    public boolean allowsClose() {
        return cue != TurnCue.INCOMPLETE;
    }
}
