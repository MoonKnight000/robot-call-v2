package uz.murodjon.robotcallv2.agent.turn;

/**
 * A {@link TurnPolicy}'s call on a pause whose wait has run out.
 *
 * @param cue    {@link TurnCue#COMPLETE} closes; {@link TurnCue#INCOMPLETE} extends the pause;
 *               {@link TurnCue#WAIT} holds it for a caller who asked for time
 * @param source the source whose verdict decided it, or null when none had one (the pause closes)
 */
public record TurnCall(TurnCue cue, TurnSource source) {

    /** Nobody objects: the pause closes on the wait it had. */
    public static final TurnCall CLOSE = new TurnCall(TurnCue.COMPLETE, null);

    /** Whether the pause is to go on past its wait. */
    public boolean holdsOpen() {
        return cue == TurnCue.INCOMPLETE || cue == TurnCue.WAIT;
    }
}
