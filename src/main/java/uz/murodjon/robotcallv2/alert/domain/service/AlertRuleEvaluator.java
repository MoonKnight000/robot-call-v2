package uz.murodjon.robotcallv2.alert.domain.service;

import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;

import java.time.Duration;
import java.time.Instant;

/** Whether a rule fires on a value. */
public final class AlertRuleEvaluator {

    private AlertRuleEvaluator() {
    }

    /**
     * The value is on the rule's side of its threshold, with enough calls behind it. A window
     * with nothing to compute from is not a breach — except for {@code CALL_COUNT} and
     * {@code TOOL_ERROR_COUNT}, where none is a value of 0.
     */
    public static boolean isBreached(AlertRule rule, AlertMetricValue value) {
        if (value.value() == null) {
            return false;
        }
        if (rule.metric().isSampleGated() && value.sampleSize() < rule.minCalls()) {
            return false;
        }
        return rule.comparator() == AlertComparator.ABOVE
                ? value.value() > rule.threshold()
                : value.value() < rule.threshold();
    }

    /** It fired less than {@code cooldownMinutes} ago, so a breach now is the same incident. */
    public static boolean isCoolingDown(AlertRule rule, Instant now) {
        return rule.lastTriggeredAt() != null
                && Duration.between(rule.lastTriggeredAt(), now).toMinutes() < rule.cooldownMinutes();
    }
}
