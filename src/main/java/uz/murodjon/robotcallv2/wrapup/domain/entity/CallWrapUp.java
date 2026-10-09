package uz.murodjon.robotcallv2.wrapup.domain.entity;

import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

import java.time.Instant;

/**
 * How an operator closed one call. The code's name and outcome are copied in, so renaming
 * or deleting the code later does not rewrite what was recorded on past calls.
 *
 * @param wrapUpCodeId null once the code has been deleted
 * @param callbackAt   when to call the client again; set only for a code that asks for it
 */
public record CallWrapUp(
        long callId,
        long companyId,
        Long wrapUpCodeId,
        String codeName,
        WrapUpOutcome outcome,
        long operatorUserId,
        String note,
        Instant callbackAt,
        Instant createdAt
) {

    /** A wrap-up to save; company and creation time are the repository's to stamp. */
    public static CallWrapUp of(long callId, WrapUpCode code, long operatorUserId, String note, Instant callbackAt) {
        return new CallWrapUp(callId, 0, code.id(), code.name(), code.outcome(), operatorUserId, note,
                callbackAt, null);
    }
}
