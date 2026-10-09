package uz.murodjon.robotcallv2.quality.domain.entity;

import java.time.Instant;

/**
 * One thing an operator's call is marked on (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.3) —
 * "greeted the client", "confirmed who they spoke to", "never promised a discount". One list
 * per company, applied to every operator call.
 *
 * @param instruction what the judge is told to look for, in the company's own words
 * @param weight      how much it counts towards the 0–100 score, 1–10
 * @param enabled     a disabled criterion is not asked about; calls already scored keep it
 */
public record ScorecardCriterion(
        long id,
        long companyId,
        String name,
        String instruction,
        int weight,
        boolean enabled,
        Instant createdAt
) {

    /** A criterion to save; id, company and creation time are the repository's to stamp. */
    public static ScorecardCriterion of(String name, String instruction, int weight, boolean enabled) {
        return new ScorecardCriterion(0, 0, name, instruction, weight, enabled, null);
    }
}
