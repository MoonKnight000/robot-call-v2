package uz.murodjon.robotcallv2.agent.dialog;

/**
 * One turn's outcome: the model's text, whether any of it reached the caller,
 * whether the fact guard is the reason it did not, and anything the tools said back
 * that the model has not been shown yet ({@code toolNote}, usually null).
 *
 * <p>Text and audio are tracked separately because they diverge in exactly the cases
 * that matter — a guard block or a TTS failure produces a perfectly good reply that
 * the caller never heard, and the caller's experience is silence.
 */
record TurnResult(String reply, boolean spoken, boolean blocked, String toolNote) {

    static final TurnResult NOTHING = new TurnResult(null, false, false, null);
}
