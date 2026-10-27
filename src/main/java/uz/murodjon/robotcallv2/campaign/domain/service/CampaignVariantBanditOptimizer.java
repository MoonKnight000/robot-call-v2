package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.domain.entity.VariantAllocation;
import uz.murodjon.robotcallv2.campaign.domain.entity.VariantOptimizationPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Autonomous Multi-Armed Bandit (MAB) optimizer for A/B testing variants.
 *
 * <p>Classical A/B testing wastes budget dialling low-performing scripts until human operators
 * notice the difference or an arbitrary trial threshold is met. This optimizer dynamically rebalances
 * traffic weights using Bayesian Laplace-smoothed rates with an exploration floor (Adaptive Bandit)
 * and shifts to exploitation (Statistical Winner) once Wilson 95% confidence intervals clear.
 *
 * <p>Spring-free by design (CLAUDE.md §4): pure arithmetic, deterministic, and unit-testable.
 */
public final class CampaignVariantBanditOptimizer {

    /** Default exploration floor per variant (in percent) to ensure ongoing learning. */
    public static final int DEFAULT_MIN_EXPLORATION_WEIGHT = 10;

    /** Minimum answered calls across all variants before shifting away from cold-start equal distribution. */
    public static final int MIN_ANSWERED_FOR_BANDIT = 20;

    /** Minimum answered calls per active variant before considering it past warmup. */
    public static final int MIN_WARMUP_PER_VARIANT = 5;

    public static final String STRATEGY_NO_ACTIVE = "NO_ACTIVE_VARIANTS";
    public static final String STRATEGY_SINGLE = "SINGLE_ACTIVE_VARIANT";
    public static final String STRATEGY_COLD_START = "COLD_START_EQUAL";
    public static final String STRATEGY_BANDIT = "ADAPTIVE_BANDIT";
    public static final String STRATEGY_WINNER = "STATISTICAL_WINNER";

    private CampaignVariantBanditOptimizer() {
    }

    /**
     * Optimizes traffic weights for the given campaign variants using the default exploration floor.
     */
    public static VariantOptimizationPlan optimize(long campaignId, long companyId, List<CampaignVariant> variants) {
        return optimize(campaignId, companyId, variants, DEFAULT_MIN_EXPLORATION_WEIGHT);
    }

    /**
     * Optimizes traffic weights for active variants.
     *
     * @param campaignId            the campaign ID
     * @param companyId             the tenant ID
     * @param variants              the list of campaign variants
     * @param minExplorationWeight  minimum traffic percentage guaranteed for each active variant
     */
    public static VariantOptimizationPlan optimize(long campaignId, long companyId, List<CampaignVariant> variants,
                                                   int minExplorationWeight) {
        Instant now = Instant.now();
        if (variants == null || variants.isEmpty()) {
            return new VariantOptimizationPlan(campaignId, companyId, STRATEGY_NO_ACTIVE, null, null, List.of(), now);
        }

        List<CampaignVariant> active = variants.stream()
                .filter(CampaignVariant::active)
                .toList();

        if (active.isEmpty()) {
            return new VariantOptimizationPlan(campaignId, companyId, STRATEGY_NO_ACTIVE, null, null, List.of(), now);
        }

        if (active.size() == 1) {
            CampaignVariant single = active.get(0);
            VariantAllocation allocation = new VariantAllocation(
                    single.id(),
                    single.name(),
                    single.trafficWeight(),
                    100,
                    single.answeredCount(),
                    single.convertedCount(),
                    single.conversionRate(),
                    percent(AbTestSignificance.lowerBound(single.convertedCount(), single.answeredCount())),
                    percent(AbTestSignificance.upperBound(single.convertedCount(), single.answeredCount()))
            );
            return new VariantOptimizationPlan(campaignId, companyId, STRATEGY_SINGLE, single.id(), single.name(),
                    List.of(allocation), now);
        }

        int n = active.size();
        int safeFloor = Math.max(1, Math.min(minExplorationWeight, 100 / (n * 2)));

        // 1. Check for statistically significant winner (Wilson 95% non-overlapping interval)
        CampaignVariant winner = AbTestSignificance.findWinner(active);
        if (winner != null) {
            int winnerWeight = 100 - (n - 1) * safeFloor;
            List<VariantAllocation> allocations = new ArrayList<>(n);
            for (CampaignVariant v : active) {
                int assignedWeight = (v.id() == winner.id()) ? winnerWeight : safeFloor;
                allocations.add(toAllocation(v, assignedWeight));
            }
            return new VariantOptimizationPlan(campaignId, companyId, STRATEGY_WINNER, winner.id(), winner.name(),
                    allocations, now);
        }

        // 2. Check for Cold Start / Warmup phase
        int totalAnswered = active.stream().mapToInt(CampaignVariant::answeredCount).sum();
        boolean hasUnwarmedVariant = active.stream().anyMatch(v -> v.answeredCount() < MIN_WARMUP_PER_VARIANT);
        if (totalAnswered < MIN_ANSWERED_FOR_BANDIT || hasUnwarmedVariant) {
            int equalBase = 100 / n;
            int remainder = 100 % n;
            List<VariantAllocation> allocations = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                CampaignVariant v = active.get(i);
                int assignedWeight = equalBase + (i < remainder ? 1 : 0);
                allocations.add(toAllocation(v, assignedWeight));
            }
            return new VariantOptimizationPlan(campaignId, companyId, STRATEGY_COLD_START, null, null,
                    allocations, now);
        }

        // 3. Adaptive Multi-Armed Bandit (Laplace-smoothed performance allocation)
        int remainingPool = 100 - (n * safeFloor);
        double[] scores = new double[n];
        double totalScore = 0.0;
        int bestVariantIndex = 0;
        double maxScore = -1.0;

        for (int i = 0; i < n; i++) {
            CampaignVariant v = active.get(i);
            // Laplace smoothed conversion rate: (conversions + 1) / (answered + 2)
            double score = (v.convertedCount() + 1.0) / (v.answeredCount() + 2.0);
            scores[i] = score;
            totalScore += score;
            if (score > maxScore) {
                maxScore = score;
                bestVariantIndex = i;
            }
        }

        int[] extraWeights = new int[n];
        int allocatedExtra = 0;
        for (int i = 0; i < n; i++) {
            int extra = (int) Math.round(remainingPool * (scores[i] / totalScore));
            extraWeights[i] = extra;
            allocatedExtra += extra;
        }

        // Adjust rounding errors on the highest-performing variant so sum is exactly 100
        int diff = remainingPool - allocatedExtra;
        extraWeights[bestVariantIndex] += diff;

        List<VariantAllocation> allocations = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CampaignVariant v = active.get(i);
            int assignedWeight = safeFloor + extraWeights[i];
            allocations.add(toAllocation(v, assignedWeight));
        }

        return new VariantOptimizationPlan(campaignId, companyId, STRATEGY_BANDIT, null, null,
                allocations, now);
    }

    private static VariantAllocation toAllocation(CampaignVariant variant, int newWeight) {
        return new VariantAllocation(
                variant.id(),
                variant.name(),
                variant.trafficWeight(),
                newWeight,
                variant.answeredCount(),
                variant.convertedCount(),
                variant.conversionRate(),
                percent(AbTestSignificance.lowerBound(variant.convertedCount(), variant.answeredCount())),
                percent(AbTestSignificance.upperBound(variant.convertedCount(), variant.answeredCount()))
        );
    }

    private static double percent(double rate) {
        return Math.round(rate * 10000.0) / 100.0;
    }
}
