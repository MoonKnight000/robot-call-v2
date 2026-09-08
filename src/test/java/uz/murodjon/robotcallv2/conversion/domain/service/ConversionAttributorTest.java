package uz.murodjon.robotcallv2.conversion.domain.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.callrecord.domain.entity.AnsweredCall;
import uz.murodjon.robotcallv2.conversion.domain.enums.AttributionModel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the rule a company disputes when a campaign's numbers look wrong, so what is
 * pinned down here is what must never be credited: a call nobody answered, a call placed
 * after the customer had already acted, and a call from before the window.
 */
class ConversionAttributorTest {

    private static final Instant PAID = Instant.parse("2026-09-07T12:00:00Z");

    private static AnsweredCall call(long id, String answeredAt) {
        return new AnsweredCall(id, 10L, 20L, Instant.parse(answeredAt));
    }

    @Test
    void creditsTheLastCallBeforeTheConversion() {
        AnsweredCall earned = ConversionAttributor.attribute(List.of(
                call(1, "2026-09-06T09:00:00Z"),
                call(2, "2026-09-07T09:00:00Z"),
                call(3, "2026-09-05T09:00:00Z")), PAID, 72, AttributionModel.LAST_CALL);

        assertThat(earned).isNotNull();
        assertThat(earned.callAttemptId()).isEqualTo(2);
    }

    @Test
    void creditsTheFirstCallWhenTheGoalSaysSo() {
        AnsweredCall earned = ConversionAttributor.attribute(List.of(
                call(1, "2026-09-06T09:00:00Z"),
                call(2, "2026-09-07T09:00:00Z"),
                call(3, "2026-09-05T09:00:00Z")), PAID, 72, AttributionModel.FIRST_CALL);

        assertThat(earned).isNotNull();
        assertThat(earned.callAttemptId()).isEqualTo(3);
    }

    /** A call placed after the payment cannot have caused it. */
    @Test
    void ignoresCallsAfterTheConversion() {
        assertThat(ConversionAttributor.attribute(List.of(
                call(1, "2026-09-07T13:00:00Z")), PAID, 72, AttributionModel.LAST_CALL)).isNull();
    }

    @Test
    void ignoresCallsOlderThanTheWindow() {
        // 24h window: a call four days earlier is outside it.
        assertThat(ConversionAttributor.attribute(List.of(
                call(1, "2026-09-03T12:00:00Z")), PAID, 24, AttributionModel.LAST_CALL)).isNull();
    }

    /** The edge belongs to the window: exactly N hours before still counts. */
    @Test
    void keepsTheCallExactlyAtTheWindowEdge() {
        assertThat(ConversionAttributor.attribute(List.of(
                call(1, "2026-09-06T12:00:00Z")), PAID, 24, AttributionModel.LAST_CALL)).isNotNull();
    }

    @Test
    void creditsNothingWhenThereIsNothingToCredit() {
        assertThat(ConversionAttributor.attribute(List.of(), PAID, 72, AttributionModel.LAST_CALL)).isNull();
        assertThat(ConversionAttributor.attribute(null, PAID, 72, AttributionModel.LAST_CALL)).isNull();
        assertThat(ConversionAttributor.attribute(List.of(call(1, "2026-09-07T09:00:00Z")),
                PAID, 0, AttributionModel.LAST_CALL)).isNull();
    }
}
