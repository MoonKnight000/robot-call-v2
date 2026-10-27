package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.domain.entity.VariantAllocation;
import uz.murodjon.robotcallv2.campaign.domain.entity.VariantOptimizationPlan;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignVariantBanditOptimizerTest {

    private static final long CAMPAIGN_ID = 42L;
    private static final long COMPANY_ID = 1L;

    @Test
    void handlesNullAndEmptyVariantsGracefully() {
        VariantOptimizationPlan nullPlan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, null);
        assertThat(nullPlan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_NO_ACTIVE);
        assertThat(nullPlan.allocations()).isEmpty();

        VariantOptimizationPlan emptyPlan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, List.of());
        assertThat(emptyPlan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_NO_ACTIVE);
        assertThat(emptyPlan.allocations()).isEmpty();
    }

    @Test
    void handlesAllInactiveVariants() {
        List<CampaignVariant> variants = List.of(
                variant(1L, "A", 50, 10, 5, false),
                variant(2L, "B", 50, 10, 5, false)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_NO_ACTIVE);
        assertThat(plan.allocations()).isEmpty();
    }

    @Test
    void singleActiveVariantGetsOneHundredPercent() {
        List<CampaignVariant> variants = List.of(
                variant(1L, "A", 50, 10, 5, true),
                variant(2L, "B", 50, 10, 5, false)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_SINGLE);
        assertThat(plan.allocations()).hasSize(1);
        assertThat(plan.allocations().get(0).optimizedTrafficWeight()).isEqualTo(100);
        assertThat(plan.winningVariantId()).isEqualTo(1L);
    }

    @Test
    void coldStartSplitsTrafficEquallyWhenTrialsAreLow() {
        // Less than 20 total answered calls
        List<CampaignVariant> variants = List.of(
                variant(1L, "A", 50, 8, 4, true),
                variant(2L, "B", 50, 8, 1, true)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_COLD_START);
        assertThat(plan.allocations()).hasSize(2);
        assertThat(plan.allocations().get(0).optimizedTrafficWeight()).isEqualTo(50);
        assertThat(plan.allocations().get(1).optimizedTrafficWeight()).isEqualTo(50);
    }

    @Test
    void coldStartEnforcesMinimumWarmupPerVariant() {
        // Total answered = 32 >= 20, but variant B only has 2 answered calls (< 5)
        List<CampaignVariant> variants = List.of(
                variant(1L, "A", 50, 30, 10, true),
                variant(2L, "B", 50, 2, 0, true)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_COLD_START);
        assertThat(plan.allocations().get(0).optimizedTrafficWeight()).isEqualTo(50);
        assertThat(plan.allocations().get(1).optimizedTrafficWeight()).isEqualTo(50);
    }

    @Test
    void coldStartDistributesOddRemainderProperly() {
        List<CampaignVariant> variants = List.of(
                variant(1L, "A", 33, 2, 0, true),
                variant(2L, "B", 33, 2, 0, true),
                variant(3L, "C", 34, 2, 0, true)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_COLD_START);
        int sum = plan.allocations().stream().mapToInt(VariantAllocation::optimizedTrafficWeight).sum();
        assertThat(sum).isEqualTo(100);
        assertThat(plan.allocations().get(0).optimizedTrafficWeight()).isEqualTo(34);
        assertThat(plan.allocations().get(1).optimizedTrafficWeight()).isEqualTo(33);
        assertThat(plan.allocations().get(2).optimizedTrafficWeight()).isEqualTo(33);
    }

    @Test
    void declaresStatisticalWinnerWhenConfidenceIntervalsClear() {
        // Overwhelming difference with large sample size: 500 answered calls each, 250 vs 50 conversions
        List<CampaignVariant> variants = List.of(
                variant(1L, "Winning Script", 50, 500, 250, true),
                variant(2L, "Losing Script", 50, 500, 50, true)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_WINNER);
        assertThat(plan.winningVariantId()).isEqualTo(1L);
        assertThat(plan.winningVariantName()).isEqualTo("Winning Script");

        // Winner gets 90% (100 - floor), loser gets 10% exploration floor
        VariantAllocation winnerAlloc = plan.allocations().stream()
                .filter(a -> a.variantId() == 1L)
                .findFirst().orElseThrow();
        VariantAllocation loserAlloc = plan.allocations().stream()
                .filter(a -> a.variantId() == 2L)
                .findFirst().orElseThrow();

        assertThat(winnerAlloc.optimizedTrafficWeight()).isEqualTo(90);
        assertThat(loserAlloc.optimizedTrafficWeight()).isEqualTo(10);
        assertThat(winnerAlloc.optimizedTrafficWeight() + loserAlloc.optimizedTrafficWeight()).isEqualTo(100);
    }

    @Test
    void adaptiveBanditReallocatesTrafficProportionallyBeforeStatisticalVerdict() {
        // Sample is past cold start (total 50 >= 20, both 25 >= 5), but intervals still overlap slightly
        // Variant A: 10/25 (40%), Variant B: 2/25 (8%)
        List<CampaignVariant> variants = List.of(
                variant(1L, "Promising Script", 50, 25, 10, true),
                variant(2L, "Lagging Script", 50, 25, 2, true)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        assertThat(plan.strategy()).isEqualTo(CampaignVariantBanditOptimizer.STRATEGY_BANDIT);
        assertThat(plan.winningVariantId()).isNull();

        VariantAllocation promising = plan.allocations().stream()
                .filter(a -> a.variantId() == 1L)
                .findFirst().orElseThrow();
        VariantAllocation lagging = plan.allocations().stream()
                .filter(a -> a.variantId() == 2L)
                .findFirst().orElseThrow();

        // Higher performing script gets significantly more traffic than lagging one
        assertThat(promising.optimizedTrafficWeight()).isGreaterThan(lagging.optimizedTrafficWeight());
        // Lagging script still gets exploration floor
        assertThat(lagging.optimizedTrafficWeight()).isGreaterThanOrEqualTo(10);
        // Total is exactly 100
        assertThat(promising.optimizedTrafficWeight() + lagging.optimizedTrafficWeight()).isEqualTo(100);
    }

    @Test
    void weightsAlwaysSumToExactlyOneHundredAcrossMultipleVariants() {
        List<CampaignVariant> variants = List.of(
                variant(1L, "A", 33, 40, 15, true),
                variant(2L, "B", 33, 40, 8, true),
                variant(3L, "C", 34, 40, 3, true)
        );

        VariantOptimizationPlan plan = CampaignVariantBanditOptimizer.optimize(CAMPAIGN_ID, COMPANY_ID, variants);
        int sum = plan.allocations().stream().mapToInt(VariantAllocation::optimizedTrafficWeight).sum();
        assertThat(sum).isEqualTo(100);

        // Verification of ordering
        assertThat(plan.allocations().get(0).optimizedTrafficWeight())
                .isGreaterThan(plan.allocations().get(1).optimizedTrafficWeight());
        assertThat(plan.allocations().get(1).optimizedTrafficWeight())
                .isGreaterThan(plan.allocations().get(2).optimizedTrafficWeight());
    }

    private static CampaignVariant variant(long id, String name, int weight, int answered, int converted, boolean active) {
        return new CampaignVariant(id, CAMPAIGN_ID, COMPANY_ID, name, null, null, null, null, weight,
                answered, answered, converted, active, Instant.now(), Instant.now());
    }
}
