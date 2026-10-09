package uz.murodjon.robotcallv2.alert.domain.service;

import uz.murodjon.robotcallv2.alert.domain.entity.AlertRule;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/** What an alert rule's settings may be. */
public final class AlertRuleValidator {

    public static final int MIN_WINDOW_MINUTES = 5;
    public static final int MAX_WINDOW_MINUTES = 1440;
    public static final int MIN_COOLDOWN_MINUTES = 5;
    public static final int MAX_COOLDOWN_MINUTES = 10080;
    public static final int MAX_MIN_CALLS = 10_000;
    /** Rules a company may have: each is a query every few minutes. */
    public static final int MAX_RULES_PER_COMPANY = 20;

    private AlertRuleValidator() {
    }

    public static void validate(AlertRule rule) {
        List<String> errors = new ArrayList<>();
        if (rule.threshold() < 0 || (rule.metric().isPercentage() && rule.threshold() > 100)) {
            errors.add("threshold: " + (rule.metric().isPercentage() ? "0–100" : ">= 0"));
        }
        if (rule.windowMinutes() < MIN_WINDOW_MINUTES || rule.windowMinutes() > MAX_WINDOW_MINUTES) {
            errors.add("windowMinutes: " + MIN_WINDOW_MINUTES + "–" + MAX_WINDOW_MINUTES);
        }
        if (rule.cooldownMinutes() < MIN_COOLDOWN_MINUTES || rule.cooldownMinutes() > MAX_COOLDOWN_MINUTES) {
            errors.add("cooldownMinutes: " + MIN_COOLDOWN_MINUTES + "–" + MAX_COOLDOWN_MINUTES);
        }
        if (rule.minCalls() < 1 || rule.minCalls() > MAX_MIN_CALLS) {
            errors.add("minCalls: 1–" + MAX_MIN_CALLS);
        }
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.ALERT_RULE_INVALID, errors, errors.size());
        }
    }
}
