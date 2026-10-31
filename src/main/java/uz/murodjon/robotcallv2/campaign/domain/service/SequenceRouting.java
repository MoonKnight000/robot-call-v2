package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * How a finished call is read: whether it settled the conversation, whether somebody answered
 * it, and whether the number is unusable.
 */
public final class SequenceRouting {

    private SequenceRouting() {
    }

    /**
     * Whether the call settled the conversation for good, for a campaign without a sequence. Any
     * other disposition — no answer, voicemail, a hang-up, a failure, a callback — leaves the
     * contact to be tried again until its attempts run out.
     */
    public static boolean isFinal(Disposition disposition) {
        return switch (disposition) {
            case PROMISE_TO_PAY, REFUSED, TRANSFERRED, WRONG_NUMBER, COMPLETED, GOAL_REACHED, GOAL_NOT_REACHED,
                 DO_NOT_CALL -> true;
            default -> false;
        };
    }

    /**
     * Whether a person answered, so the sequence's outcomes are judged (Retell: voicemail, no
     * answer, busy and a failed dial count as unanswered and move on to the next attempt).
     */
    public static boolean isAnswered(Disposition disposition) {
        return switch (disposition) {
            case NO_ANSWER, CARRIER_REJECTED, HUNG_UP, VOICEMAIL, FAILED -> false;
            default -> true;
        };
    }

    /**
     * Whether the number must not be tried again, whatever the outcomes say: one the carrier
     * rejects, one that is somebody else's, and one that asked not to be called.
     */
    public static boolean endsSequence(Disposition disposition) {
        return disposition == Disposition.CARRIER_REJECTED || disposition == Disposition.WRONG_NUMBER
                || disposition == Disposition.DO_NOT_CALL;
    }
}
