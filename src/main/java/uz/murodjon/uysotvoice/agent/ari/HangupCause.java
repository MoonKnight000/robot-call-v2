package uz.murodjon.uysotvoice.agent.ari;

import uz.murodjon.uysotvoice.shared.dialog.Disposition;

/**
 * Maps an Asterisk/Q.850 hangup cause code to a {@link Disposition} (PROJECT.md §8.6).
 *
 * <p>Without this every call that produced no dialog outcome looked the same, and the
 * dialer treated them the same: a disconnected number burned all three of its retry
 * attempts before being marked EXHAUSTED, and a busy signal was indistinguishable from
 * a subscriber who let it ring. The cause code is the only signal that separates them,
 * and Asterisk reports it on {@code ChannelHangupRequest} / {@code ChannelDestroyed}.
 *
 * <p>{@link #toDisposition} returns {@code null} rather than guessing when the cause
 * says nothing about intent — cause 16 (normal clearing) covers both "hung up in anger"
 * and "agreed to pay and said goodbye", and only the conversation knows which. Callers
 * use the dialog's own outcome first and fall back here.
 */
public final class HangupCause {

    /** Normal clearing — the call ended cleanly; the conversation decides the outcome. */
    public static final int NORMAL_CLEARING = 16;

    private HangupCause() {
    }

    /**
     * The disposition implied by {@code cause}, or {@code null} if the code carries no
     * verdict (unknown, or normal clearing).
     */
    public static Disposition toDisposition(Integer cause) {
        if (cause == null) {
            return null;
        }
        return switch (cause) {
            // 1 unallocated number, 22 number changed, 3 no route to destination:
            // the number itself is wrong, so retrying it is pointless (and WRONG_NUMBER
            // is terminal — see CampaignService.isTerminal).
            case 1, 3, 22 -> Disposition.WRONG_NUMBER;
            // 17 user busy, 18 no user responding, 19 no answer, 20 subscriber absent:
            // the subscriber may well answer later, so these are retried.
            case 17, 18, 19, 20 -> Disposition.NO_ANSWER;
            // 21 call rejected — the far end actively refused it.
            case 21 -> Disposition.REFUSED;
            // 34 no circuit available, 38 network out of order, 42 switching equipment
            // congestion, 44 requested channel unavailable: our side or the carrier
            // failed, not the subscriber. FAILED retries fastest.
            case 34, 38, 42, 44 -> Disposition.FAILED;
            default -> null;
        };
    }

    /**
     * Whether {@code cause} means the number can never be reached, so no further
     * attempt should be made regardless of the attempt count.
     */
    public static boolean isUnreachable(Integer cause) {
        return toDisposition(cause) == Disposition.WRONG_NUMBER;
    }

    /** Short human-readable label for the log and the {@code hangup_cause} column. */
    public static String label(Integer cause) {
        if (cause == null) {
            return null;
        }
        String text = switch (cause) {
            case 1 -> "unallocated number";
            case 3 -> "no route to destination";
            case 16 -> "normal clearing";
            case 17 -> "user busy";
            case 18 -> "no user responding";
            case 19 -> "no answer";
            case 20 -> "subscriber absent";
            case 21 -> "call rejected";
            case 22 -> "number changed";
            case 34 -> "no circuit available";
            case 38 -> "network out of order";
            case 42 -> "switching equipment congestion";
            case 44 -> "requested channel unavailable";
            default -> "cause " + cause;
        };
        return cause + " (" + text + ")";
    }
}
