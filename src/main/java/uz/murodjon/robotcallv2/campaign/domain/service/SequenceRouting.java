package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceOutcome;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * How a finished call is read by a campaign sequence: whether it settled the conversation or
 * the contact may be tried again, and which {@link SequenceOutcome} it routes on.
 */
public final class SequenceRouting {

    private SequenceRouting() {
    }

    /**
     * Whether the call settled the conversation for good. Any other disposition — no answer,
     * voicemail, a hang-up, a failure, a callback — leaves the contact to be tried again until
     * the step's attempts run out.
     */
    public static boolean isFinal(Disposition disposition) {
        return switch (disposition) {
            case PROMISE_TO_PAY, REFUSED, TRANSFERRED, WRONG_NUMBER, COMPLETED, GOAL_REACHED, GOAL_NOT_REACHED,
                 DO_NOT_CALL -> true;
            default -> false;
        };
    }

    /**
     * Whether the contact must not be taken to any further step, whatever the routes say: one who
     * asked not to be called again, and a number that is somebody else's.
     */
    public static boolean endsSequence(Disposition disposition) {
        return disposition == Disposition.DO_NOT_CALL || disposition == Disposition.WRONG_NUMBER;
    }

    /** The outcome a call routes on once it is final or its step's attempts are spent. */
    public static SequenceOutcome outcomeOf(Disposition disposition) {
        return switch (disposition) {
            case PROMISE_TO_PAY, COMPLETED, GOAL_REACHED -> SequenceOutcome.SUCCESS;
            case REFUSED, GOAL_NOT_REACHED -> SequenceOutcome.NOT_INTERESTED;
            case DO_NOT_CALL -> SequenceOutcome.DO_NOT_CALL;
            case NO_ANSWER, CARRIER_REJECTED, HUNG_UP, VOICEMAIL, FAILED -> SequenceOutcome.NO_RESPONSE;
            case TRANSFERRED, WRONG_NUMBER, CALLBACK_REQUESTED, PAYMENT_CLAIMED -> SequenceOutcome.OTHER;
        };
    }
}
