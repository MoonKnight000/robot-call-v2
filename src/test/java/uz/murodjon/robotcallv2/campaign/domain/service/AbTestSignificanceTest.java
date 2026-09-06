package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of this rule is to <em>refuse</em> to name a winner most of the time, so the
 * cases that matter are the ones where an operator would otherwise have rewritten a script
 * on three calls' worth of noise.
 */
class AbTestSignificanceTest {

    @Test
    void intervalStaysInsideZeroToOne() {
        // 1 conversion in 12 is where the textbook normal interval goes negative.
        assertThat(AbTestSignificance.lowerBound(1, 12)).isGreaterThanOrEqualTo(0.0);
        assertThat(AbTestSignificance.upperBound(1, 12)).isLessThanOrEqualTo(1.0);
        assertThat(AbTestSignificance.lowerBound(12, 12)).isGreaterThan(0.0);
        assertThat(AbTestSignificance.upperBound(0, 12)).isLessThan(1.0);
    }

    @Test
    void noTrialsIsNoEvidenceRatherThanARateOfZero() {
        assertThat(AbTestSignificance.lowerBound(0, 0)).isEqualTo(0.0);
        assertThat(AbTestSignificance.upperBound(0, 0)).isEqualTo(1.0);
    }

    @Test
    void intervalNarrowsAsCallsAccumulate() {
        double wideSpread = AbTestSignificance.upperBound(5, 10) - AbTestSignificance.lowerBound(5, 10);
        double tightSpread = AbTestSignificance.upperBound(500, 1000) - AbTestSignificance.lowerBound(500, 1000);

        assertThat(tightSpread).isLessThan(wideSpread);
    }

    @Test
    void namesNoWinnerOnAHandfulOfCalls() {
        // 3/4 against 1/4 looks decisive and is not: four calls each.
        List<CampaignVariant> variants = List.of(variant("A", 4, 3), variant("B", 4, 1));

        assertThat(AbTestSignificance.findWinner(variants)).isNull();
    }

    @Test
    void namesNoWinnerWhenIntervalsOverlap() {
        // 40% against 33% on 100 answered calls each — a real gap, still inside the noise.
        List<CampaignVariant> variants = List.of(variant("A", 100, 40), variant("B", 100, 33));

        assertThat(AbTestSignificance.findWinner(variants)).isNull();
    }

    @Test
    void namesAWinnerWhenTheGapClearsBothIntervals() {
        List<CampaignVariant> variants = List.of(variant("A", 500, 250), variant("B", 500, 50));

        assertThat(AbTestSignificance.findWinner(variants)).isNotNull();
        assertThat(AbTestSignificance.findWinner(variants).name()).isEqualTo("A");
    }

    @Test
    void aSingleVariantHasBeatenNothing() {
        assertThat(AbTestSignificance.findWinner(List.of(variant("A", 500, 250)))).isNull();
        assertThat(AbTestSignificance.findWinner(List.of())).isNull();
        assertThat(AbTestSignificance.findWinner(null)).isNull();
    }

    private static CampaignVariant variant(String name, int answered, int converted) {
        return new CampaignVariant(1L, 1L, 1L, name, null, null, null, null, 50,
                answered, answered, converted, true, Instant.now(), Instant.now());
    }
}
