package uz.murodjon.robotcallv2.wrapup.domain.entity;

import uz.murodjon.robotcallv2.wrapup.domain.enums.WrapUpOutcome;

import java.time.Instant;

/**
 * One result an operator can close a call with — "Sotildi", "Qayta qo'ng'iroq", "Noto'g'ri
 * raqam". One list per company, shared by every queue and campaign.
 *
 * @param requiresCallbackAt whether the operator must say when to call the client again
 * @param enabled            a disabled code stays on the calls that used it, but cannot be picked
 */
public record WrapUpCode(
        long id,
        long companyId,
        String name,
        WrapUpOutcome outcome,
        boolean requiresCallbackAt,
        boolean enabled,
        Instant createdAt
) {

    /** A code to save; id, company and creation time are the repository's to stamp. */
    public static WrapUpCode of(String name, WrapUpOutcome outcome, boolean requiresCallbackAt, boolean enabled) {
        return new WrapUpCode(0, 0, name, outcome, requiresCallbackAt, enabled, null);
    }
}
