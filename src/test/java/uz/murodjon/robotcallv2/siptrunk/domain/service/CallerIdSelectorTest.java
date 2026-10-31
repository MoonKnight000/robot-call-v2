package uz.murodjon.robotcallv2.siptrunk.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdStrategy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CallerIdSelectorTest {

    private final Instant now = Instant.parse("2026-10-09T12:00:00Z");

    /** Only a number on one of the offered trunks is chosen; one with no trunk never is. */
    @Test
    void selectOnTrunksSkipsNumbersOffTheOfferedTrunks() {
        CallerIdPoolNumber offTrunk = new CallerIdPoolNumber(1, 10, "+998711111111", "A", 0, 0,
                CallerIdNumberStatus.ACTIVE, null, null, now, 1L);
        CallerIdPoolNumber noTrunk = new CallerIdPoolNumber(2, 10, "+998712222222", "B", 0, 0,
                CallerIdNumberStatus.ACTIVE, null, null, now, null);
        CallerIdPoolNumber onTrunk = new CallerIdPoolNumber(3, 10, "+998713333333", "C", 50, 20,
                CallerIdNumberStatus.ACTIVE, null, now, now, 2L);

        Optional<CallerIdPoolNumber> selected = CallerIdSelector.selectOnTrunks(List.of(offTrunk, noTrunk, onTrunk),
                CallerIdStrategy.ROUND_ROBIN, now, null, Set.of(2L));

        assertThat(selected).contains(onTrunk);
        assertThat(CallerIdSelector.selectOnTrunks(List.of(offTrunk, noTrunk), CallerIdStrategy.ROUND_ROBIN, now,
                null, Set.of(2L))).isEmpty();
    }

    @Test
    void selectsLeastUsedWhenStrategyIsLeastUsed() {
        CallerIdPoolNumber n1 = new CallerIdPoolNumber(1, 10, "+998711111111", "A", 100, 50, CallerIdNumberStatus.ACTIVE, null, now.minus(1, ChronoUnit.HOURS), now, 1L);
        CallerIdPoolNumber n2 = new CallerIdPoolNumber(2, 10, "+998712222222", "B", 10, 5, CallerIdNumberStatus.ACTIVE, null, now.minus(2, ChronoUnit.HOURS), now, 1L);

        Optional<CallerIdPoolNumber> selected = CallerIdSelector.select(List.of(n1, n2), CallerIdStrategy.LEAST_USED, now);

        assertThat(selected).isPresent();
        assertThat(selected.get().phone()).isEqualTo("+998712222222");
    }

    @Test
    void selectsHighestAnswerRate() {
        CallerIdPoolNumber n1 = new CallerIdPoolNumber(1, 10, "+998711111111", "A", 100, 80, CallerIdNumberStatus.ACTIVE, null, now, now, 1L); // 80%
        CallerIdPoolNumber n2 = new CallerIdPoolNumber(2, 10, "+998712222222", "B", 100, 40, CallerIdNumberStatus.ACTIVE, null, now, now, 1L); // 40%

        Optional<CallerIdPoolNumber> selected = CallerIdSelector.select(List.of(n1, n2), CallerIdStrategy.HIGHEST_ANSWER_RATE, now);

        assertThat(selected).isPresent();
        assertThat(selected.get().phone()).isEqualTo("+998711111111");
    }

    @Test
    void skipsCoolingDownAndDisabledNumbers() {
        CallerIdPoolNumber cooling = new CallerIdPoolNumber(1, 10, "+998711111111", "A", 10, 1,
                CallerIdNumberStatus.COOLING_DOWN, now.plus(1, ChronoUnit.HOURS), now, now, 1L);
        CallerIdPoolNumber disabled = new CallerIdPoolNumber(2, 10, "+998712222222", "B", 5, 1,
                CallerIdNumberStatus.DISABLED, null, now, now, 1L);
        CallerIdPoolNumber active = new CallerIdPoolNumber(3, 10, "+998713333333", "C", 50, 20,
                CallerIdNumberStatus.ACTIVE, null, now, now, 1L);

        Optional<CallerIdPoolNumber> selected = CallerIdSelector.select(List.of(cooling, disabled, active), CallerIdStrategy.LEAST_USED, now);

        assertThat(selected).isPresent();
        assertThat(selected.get().phone()).isEqualTo("+998713333333");
    }

    @Test
    void returnsEmptyWhenNoNumbersEligible() {
        CallerIdPoolNumber cooling = new CallerIdPoolNumber(1, 10, "+998711111111", "A", 10, 1,
                CallerIdNumberStatus.COOLING_DOWN, now.plus(1, ChronoUnit.HOURS), now, now, 1L);
        CallerIdPoolNumber disabled = new CallerIdPoolNumber(2, 10, "+998712222222", "B", 5, 1,
                CallerIdNumberStatus.DISABLED, null, now, now, 1L);

        Optional<CallerIdPoolNumber> selected = CallerIdSelector.select(List.of(cooling, disabled), CallerIdStrategy.ROUND_ROBIN, now);

        assertThat(selected).isEmpty();
    }
}
