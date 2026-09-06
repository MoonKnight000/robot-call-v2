package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property that makes the A/B numbers mean anything is that a retry of the same number
 * lands on the same script, so most of this file is about stability rather than fairness.
 */
class CampaignVariantSelectorTest {

    private static final List<CampaignVariant> TWO_EVEN =
            List.of(variant(1L, "A", 50, true), variant(2L, "B", 50, true));

    @Test
    void sameNumberAlwaysGetsTheSameVariant() {
        CampaignVariant first = CampaignVariantSelector.selectVariant(TWO_EVEN, "998901234567");

        for (int i = 0; i < 50; i++) {
            assertThat(CampaignVariantSelector.selectVariant(TWO_EVEN, "998901234567"))
                    .isEqualTo(first);
        }
    }

    @Test
    void theSameNumberInAnotherCampaignIsAssignedIndependently() {
        // Same weights, same names, different campaign id: over a list of numbers the two
        // campaigns must not produce identical assignments, or their results are correlated.
        List<CampaignVariant> otherCampaign =
                List.of(variantOfCampaign(7L, 3L, "A", 50), variantOfCampaign(7L, 4L, "B", 50));

        int differing = 0;
        for (int i = 0; i < 200; i++) {
            String phone = "99890" + (1000000 + i);
            String here = CampaignVariantSelector.selectVariant(TWO_EVEN, phone).name();
            String there = CampaignVariantSelector.selectVariant(otherCampaign, phone).name();
            if (!here.equals(there)) {
                differing++;
            }
        }

        assertThat(differing).isBetween(60, 140);
    }

    @Test
    void consecutiveNumbersAreNotDealtOutInAPattern() {
        // A CSV is usually sorted, so consecutive numbers are the realistic worst case for
        // a weak hash: each variant should still get roughly half of them.
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String name = CampaignVariantSelector.selectVariant(TWO_EVEN, "99890" + (1000000 + i)).name();
            counts.merge(name, 1, Integer::sum);
        }

        assertThat(counts.get("A")).isBetween(430, 570);
        assertThat(counts.get("B")).isBetween(430, 570);
    }

    @Test
    void respectsTrafficWeight() {
        List<CampaignVariant> skewed =
                List.of(variant(1L, "A", 90, true), variant(2L, "B", 10, true));

        int b = 0;
        for (int i = 0; i < 1000; i++) {
            if ("B".equals(CampaignVariantSelector.selectVariant(skewed, "99890" + (1000000 + i)).name())) {
                b++;
            }
        }

        assertThat(b).isBetween(60, 140);
    }

    @Test
    void skipsInactiveVariants() {
        List<CampaignVariant> variants =
                List.of(variant(1L, "A", 50, false), variant(2L, "B", 50, true));

        for (int i = 0; i < 20; i++) {
            assertThat(CampaignVariantSelector.selectVariant(variants, "99890" + i).name()).isEqualTo("B");
        }
    }

    @Test
    void noActiveVariantMeansTheCampaignsOwnScenario() {
        assertThat(CampaignVariantSelector.selectVariant(List.of(), "998901234567")).isNull();
        assertThat(CampaignVariantSelector.selectVariant(null, "998901234567")).isNull();
        assertThat(CampaignVariantSelector.selectVariant(
                List.of(variant(1L, "A", 50, false)), "998901234567")).isNull();
    }

    @Test
    void aCallWithNoNumberStillGetsAVariant() {
        assertThat(CampaignVariantSelector.selectVariant(TWO_EVEN, null)).isNotNull();
        assertThat(CampaignVariantSelector.selectVariant(TWO_EVEN, "  ")).isNotNull();
    }

    private static CampaignVariant variant(long id, String name, int weight, boolean active) {
        return new CampaignVariant(id, 1L, 1L, name, null, null, null, null, weight,
                0, 0, 0, active, Instant.now(), Instant.now());
    }

    private static CampaignVariant variantOfCampaign(long campaignId, long id, String name, int weight) {
        return new CampaignVariant(id, campaignId, 1L, name, null, null, null, null, weight,
                0, 0, 0, true, Instant.now(), Instant.now());
    }
}
