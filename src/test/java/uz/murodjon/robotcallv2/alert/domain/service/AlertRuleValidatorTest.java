package uz.murodjon.robotcallv2.alert.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleValidatorTest {

    @Test
    void sensibleSettingsPass() {
        assertThatCode(() -> AlertRuleValidator.validate(rule(AlertMetric.SUCCESS_RATE, 40, 60, 10, 60)))
                .doesNotThrowAnyException();
        assertThatCode(() -> AlertRuleValidator.validate(rule(AlertMetric.AVG_TURN_LATENCY_MS, 1500, 60, 10, 60)))
                .doesNotThrowAnyException();
    }

    @Test
    void aRateCannotBeOver100Percent() {
        assertThatThrownBy(() -> AlertRuleValidator.validate(rule(AlertMetric.SUCCESS_RATE, 140, 60, 10, 60)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void everyProblemIsListed() {
        assertThatThrownBy(() -> AlertRuleValidator.validate(rule(AlertMetric.CALL_COUNT, -1, 1, 0, 1)))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.errors()).hasSize(4));
    }

    private static AlertRule rule(AlertMetric metric, double threshold, int windowMinutes, int minCalls,
                                  int cooldownMinutes) {
        return AlertRule.settings(1L, "rule", metric, AlertComparator.BELOW, threshold, windowMinutes, minCalls,
                cooldownMinutes, null, true);
    }
}
