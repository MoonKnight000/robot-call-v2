package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * Result of an autonomous multi-armed bandit optimization run.
 *
 * @param campaignId         the campaign whose variants were optimized
 * @param companyId          the tenant ID
 * @param strategy           the strategy selected: COLD_START_EQUAL, ADAPTIVE_BANDIT, STATISTICAL_WINNER, SINGLE_ACTIVE_VARIANT, NO_ACTIVE_VARIANTS
 * @param winningVariantId   the variant ID if a statistical winner was declared; null if inconclusive
 * @param winningVariantName the variant name if a statistical winner was declared; null if inconclusive
 * @param allocations        the variant allocations with old and new weights
 * @param optimizedAt        when the optimization calculation occurred
 */
public record VariantOptimizationPlan(
        long campaignId,
        long companyId,
        String strategy,
        Long winningVariantId,
        String winningVariantName,
        List<VariantAllocation> allocations,
        Instant optimizedAt
) {
}
