package uz.murodjon.robotcallv2.operator.application.port.input;

import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.util.List;

/** Every operator's live presence — what the queue asks before and after each offer. */
public interface OperatorPresenceUseCase {

    /** The operator's presence right now; OFFLINE for one not seen since the start. */
    OperatorPresence find(long companyId, long userId);

    /** Every operator of the company seen since the start, whatever their status. */
    List<OperatorPresence> findByCompany(long companyId);

    /** A status the operator picked in the panel. */
    OperatorPresence setStatus(long companyId, long userId, OperatorStatus status, String reason);

    /**
     * Take an AVAILABLE operator for one offer. False when someone else got there first or
     * the operator is no longer available — the caller then tries the next candidate.
     */
    boolean claimForRinging(long userId, String channelId);

    /** The offer was withdrawn before the operator answered — not their fault, back to AVAILABLE. */
    void releaseRinging(long userId);

    /** The operator let the offer ring out. Twice in a row takes them off the queue. */
    void markMissed(long userId);

    void markOnCall(long userId, String channelId);

    /** The call ended; the operator wraps up for {@code wrapUpSec}, then is AVAILABLE again. */
    void markCallEnded(long userId, int wrapUpSec);

    /** The operator recorded the call's result: a wrap-up still running ends now. */
    void finishWrapUp(long userId);
}
