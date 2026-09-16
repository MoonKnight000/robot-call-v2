package uz.murodjon.robotcallv2.agent.dialog;

/**
 * A write tool's call that was read back to the caller instead of being sent
 * (UNIVERSAL-OPERATOR-PLAN B.3, {@link HttpToolExecutor}).
 *
 * @param arguments        the call's arguments as canonical JSON — the yes covers exactly these
 * @param callerUtterances how many times the caller had spoken when it was read back; the
 *                         yes has to come after, not in the same breath
 */
public record PendingToolConfirmation(String arguments, int callerUtterances) {
}
