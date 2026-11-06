package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;

import java.time.Instant;

/**
 * One model's priced share of one LLM charge.
 *
 * @param id                storage's; null before it is written
 * @param companyId         whose balance it came from
 * @param purpose           what the charge was for
 * @param referenceType     what was charged, as the ledger names it ({@code call_attempt}, {@code copilot_message})
 * @param referenceId       its id
 * @param cost              the model, its tokens, the prices used and the amounts
 * @param markupBasisPoints the markup the amounts include
 * @param createdAt         storage's to stamp
 */
public record LlmUsageLine(
        Long id,
        long companyId,
        LlmUsagePurpose purpose,
        String referenceType,
        String referenceId,
        ModelLlmCost cost,
        int markupBasisPoints,
        Instant createdAt
) {

    /** A line to write: id and time are storage's. */
    public static LlmUsageLine of(long companyId, LlmUsagePurpose purpose, String referenceType, String referenceId,
                                  ModelLlmCost cost, int markupBasisPoints) {
        return new LlmUsageLine(null, companyId, purpose, referenceType, referenceId, cost, markupBasisPoints, null);
    }
}
