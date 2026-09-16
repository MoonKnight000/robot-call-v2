package uz.murodjon.robotcallv2.agent.alerting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alerting thresholds, windows, and schedule configuration bound from
 * {@code voice-agent.alerting.*} (PROJECT.md §10 Bosqich 12).
 *
 * @param enabled               when false, alerting checks do not run
 * @param windowMinutes         lookback window for the success-rate check (minutes)
 * @param minSample             calls the window must hold before its rate is judged
 * @param successThreshold      minimum acceptable success rate — the share of ended calls
 *                              that are a conversion ({@code Disposition#isConversion}:
 *                              PROMISE_TO_PAY, GOAL_REACHED or COMPLETED)
 * @param checkMinutes          schedule interval for alerting checks (minutes)
 * @param turnaroundBudgetMs    p95 of caller-stops-speaking to first bot audio on the wire
 *                              (§1.3). 1000 ms is the budget the spec sets, so an alert
 *                              here means the pipeline is outside what the project
 *                              promised — raise it as a deliberate decision about the
 *                              promise, never to quieten the alert
 * @param turnaroundMinTurns    turns the window must hold before its p95 is judged; a
 *                              handful of turns says nothing
 * @param speculationMinHitRate share of speculative replies ({@code dialog.preemptive})
 *                              the final transcript has to confirm. A miss is generated,
 *                              billed and thrown away, so below this the guesses cost more
 *                              than the latency they buy — raise {@code
 *                              preemptive-min-chars}, or turn preemptive off
 * @param providerFailoverThreshold failovers between check runs — STT moved to another
 *                              vendor, TTS spoken by another vendor, a realtime call that
 *                              lost its engine — above which a provider is taken to be
 *                              having an incident. Each one is a caller who heard something
 *                              go wrong; a few per window are the failover working, a burst
 *                              is an outage it is papering over
 * @param minMeanMos            mean estimated MOS of the calls that ended in the window
 *                              below which the network, not the agent, is the problem —
 *                              3.5 is where narrowband calls start to be called "bad"
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
        double speculationMinHitRate,
        int providerFailoverThreshold,
        double minMeanMos
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
        if (providerFailoverThreshold <= 0) {
            providerFailoverThreshold = 5;
        }
        if (minMeanMos <= 0.0) {
            minMeanMos = 3.5;
        }
    }
}
