package uz.murodjon.robotcallv2.alert.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void aRateBelowItsThresholdWithEnoughCallsIsABreach() {
        AlertRule rule = rule(AlertMetric.SUCCESS_RATE, AlertComparator.BELOW, 40, null);

        assertThat(AlertRuleEvaluator.isBreached(rule, new AlertMetricValue(32.0, 54))).isTrue();
        assertThat(AlertRuleEvaluator.isBreached(rule, new AlertMetricValue(40.0, 54))).isFalse();
    }

    @Test
    void tooFewCallsAreNotJudged() {
        AlertRule rule = rule(AlertMetric.SUCCESS_RATE, AlertComparator.BELOW, 40, null);

        assertThat(AlertRuleEvaluator.isBreached(rule, new AlertMetricValue(0.0, 3))).isFalse();
    }

    @Test
    void anEmptyWindowIsNotABreach() {
        AlertRule rule = rule(AlertMetric.AVG_TURN_LATENCY_MS, AlertComparator.ABOVE, 1500, null);

        assertThat(AlertRuleEvaluator.isBreached(rule, new AlertMetricValue(null, 0))).isFalse();
    }

    @Test
    void noCallsAtAllBreachesACallCountFloor() {
        AlertRule rule = rule(AlertMetric.CALL_COUNT, AlertComparator.BELOW, 1, null);

        assertThat(AlertRuleEvaluator.isBreached(rule, new AlertMetricValue(0.0, 0))).isTrue();
    }

    @Test
    void aRuleThatFiredWithinItsCooldownStaysQuiet() {
        assertThat(AlertRuleEvaluator.isCoolingDown(
                rule(AlertMetric.SUCCESS_RATE, AlertComparator.BELOW, 40, NOW.minusSeconds(59 * 60)), NOW)).isTrue();
        assertThat(AlertRuleEvaluator.isCoolingDown(
                rule(AlertMetric.SUCCESS_RATE, AlertComparator.BELOW, 40, NOW.minusSeconds(60 * 60)), NOW)).isFalse();
        assertThat(AlertRuleEvaluator.isCoolingDown(
                rule(AlertMetric.SUCCESS_RATE, AlertComparator.BELOW, 40, null), NOW)).isFalse();
    }

    private static AlertRule rule(AlertMetric metric, AlertComparator comparator, double threshold,
                                  Instant lastTriggeredAt) {
        return new AlertRule(1L, 1L, "rule", metric, comparator, threshold, 60, 10, 60, null, true,
                null, null, lastTriggeredAt, NOW);
    }
}
