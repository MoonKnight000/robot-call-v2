package uz.murodjon.robotcallv2.agent.alerting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alerting thresholds, windows, and schedule configuration bound from
 * {@code voice-agent.alerting.*} (PROJECT.md §10 Bosqich 12).
 *
 * @param enabled               when false, alerting checks do not run
 * @param windowMinutes         lookback window for success rate check (minutes)
 * @param minSample             minimum call sample count required to evaluate success rate
 * @param successThreshold      minimum acceptable success rate (promise to pay)
 * @param checkMinutes          schedule interval for alerting checks (minutes)
 * @param turnaroundBudgetMs    §1.3 turnaround budget p95 limit in milliseconds
 * @param turnaroundMinTurns    minimum turns in window before evaluating turnaround
 * @param speculationMinHitRate minimum hit rate for speculative reply generation
 */
@ConfigurationProperties(prefix = "voice-agent.alerting")
public record AlertingProperties(
        boolean enabled,
        int windowMinutes,
        int minSample,
        double successThreshold,
        int checkMinutes,
        int turnaroundBudgetMs,
        int turnaroundMinTurns,
        double speculationMinHitRate
) {

    public AlertingProperties {
        if (windowMinutes <= 0) {
            windowMinutes = 30;
        }
        if (minSample <= 0) {
            minSample = 20;
        }
        if (successThreshold <= 0.0) {
            successThreshold = 0.3;
        }
        if (checkMinutes <= 0) {
            checkMinutes = 5;
        }
        if (turnaroundBudgetMs <= 0) {
            turnaroundBudgetMs = 1000;
        }
        if (turnaroundMinTurns <= 0) {
            turnaroundMinTurns = 30;
        }
        if (speculationMinHitRate <= 0.0) {
            speculationMinHitRate = 0.5;
        }
    }
}
