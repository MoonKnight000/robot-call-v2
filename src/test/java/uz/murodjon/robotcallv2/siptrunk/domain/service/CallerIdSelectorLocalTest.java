package uz.murodjon.robotcallv2.siptrunk.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Local presence: the pool number closest to the callee's own. */
class CallerIdSelectorLocalTest {

    private final Instant now = Instant.parse("2026-10-31T12:00:00Z");

    private CallerIdPoolNumber number(long id, String phone, CallerIdNumberStatus status) {
        return new CallerIdPoolNumber(id, 10, phone, "n" + id, 0, 0, status, null,
                now.minus(id, ChronoUnit.HOURS), now);
    }

    @Test
    void picksTheNumberOfTheCalleesOwnRegion() {
        CallerIdPoolNumber tashkent = number(1, "+998712001122", CallerIdNumberStatus.ACTIVE);
        CallerIdPoolNumber samarkand = number(2, "+998662001122", CallerIdNumberStatus.ACTIVE);

        assertThat(CallerIdSelector.selectLocal(List.of(samarkand, tashkent), CallerIdStrategy.ROUND_ROBIN, now,
                "+998712345678")).contains(tashkent);
        assertThat(CallerIdSelector.selectLocal(List.of(samarkand, tashkent), CallerIdStrategy.ROUND_ROBIN, now,
                "998662345678")).contains(samarkand);
    }

    @Test
    void theStrategyPicksAmongTheNumbersOfThatRegion() {
        CallerIdPoolNumber recent = number(1, "+998712001122", CallerIdNumberStatus.ACTIVE);
        CallerIdPoolNumber lessRecent = number(2, "+998712001123", CallerIdNumberStatus.ACTIVE);
        CallerIdPoolNumber elsewhere = number(5, "+998662001122", CallerIdNumberStatus.ACTIVE);

        // ROUND_ROBIN takes the one used longest ago, but only among the Tashkent ones.
        assertThat(CallerIdSelector.selectLocal(List.of(recent, lessRecent, elsewhere),
                CallerIdStrategy.ROUND_ROBIN, now, "+998712345678")).contains(lessRecent);
    }

    @Test
    void aRegionTheNumberDoesNotCoverFallsBackToTheWholePool() {
        CallerIdPoolNumber tashkent = number(1, "+998712001122", CallerIdNumberStatus.ACTIVE);

        assertThat(CallerIdSelector.selectLocal(List.of(tashkent), CallerIdStrategy.ROUND_ROBIN, now,
                "+998902345678")).contains(tashkent);
    }

    @Test
    void aDisabledLocalNumberIsNotUsedEvenThoughItIsTheClosest() {
        CallerIdPoolNumber disabledLocal = number(1, "+998712001122", CallerIdNumberStatus.DISABLED);
        CallerIdPoolNumber activeElsewhere = number(2, "+998662001122", CallerIdNumberStatus.ACTIVE);

        assertThat(CallerIdSelector.selectLocal(List.of(disabledLocal, activeElsewhere),
                CallerIdStrategy.ROUND_ROBIN, now, "+998712345678")).contains(activeElsewhere);
    }

    @Test
    void anEmptyPoolSelectsNothing() {
        assertThat(CallerIdSelector.selectLocal(List.of(), CallerIdStrategy.ROUND_ROBIN, now, "+998712345678"))
                .isEmpty();
    }
}
