package uz.murodjon.robotcallv2.inbound.domain.entity;

import uz.murodjon.robotcallv2.inbound.domain.enums.CallerRuleAction;

import java.time.Instant;

/**
 * Who is refused or put first before any flow runs (docs/INBOUND-PBX-PLAN.md §9).
 *
 * @param phonePrefix digits the caller's number starts with ("99890", a whole number), or
 *                    {@link #ANONYMOUS} for callers who hide their number
 * @param message     said by {@link CallerRuleAction#BLOCK_MESSAGE}
 */
public record InboundCallerRule(
        long id,
        long companyId,
        String phonePrefix,
        CallerRuleAction action,
        String message,
        String note,
        Instant createdAt
) {

    /** The prefix that matches a call with no caller number at all. */
    public static final String ANONYMOUS = "ANONYMOUS";
}
