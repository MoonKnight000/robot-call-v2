package uz.murodjon.robotcallv2.alert.domain.entity;

import uz.murodjon.robotcallv2.alert.domain.enums.AlertComparator;
import uz.murodjon.robotcallv2.alert.domain.enums.AlertMetric;

import java.time.Instant;

/**
 * A threshold a company sets on its own calls: when {@code metric} over the last
 * {@code windowMinutes} goes {@code comparator} {@code threshold}, the company gets an
 * {@code ALERT_TRIGGERED} notification — in the panel and on whichever channels its
 * notification settings send that type to.
 *
 * @param aiAgentId       null — every AI agent's calls; set — that agent's only
 * @param minCalls        a sample-gated metric is not judged on fewer calls than this
 * @param cooldownMinutes after firing, the rule stays quiet this long even if still breached
 * @param lastValue       the metric at the last evaluation; null when it had nothing to judge
 */
public record AlertRule(
        Long id,
        long companyId,
        String name,
        AlertMetric metric,
        AlertComparator comparator,
        double threshold,
        int windowMinutes,
        int minCalls,
        int cooldownMinutes,
        Long aiAgentId,
        boolean enabled,
        Double lastValue,
        Instant lastEvaluatedAt,
        Instant lastTriggeredAt,
        Instant createdAt
) {

    /**
     * The settings of a rule to write, without the evaluation state; the adapter keeps that
     * state on an update and stamps id and createdAt on a create.
     */
    public static AlertRule settings(long companyId, String name, AlertMetric metric, AlertComparator comparator,
                                     double threshold, int windowMinutes, int minCalls, int cooldownMinutes,
                                     Long aiAgentId, boolean enabled) {
        return new AlertRule(null, companyId, name, metric, comparator, threshold, windowMinutes, minCalls,
                cooldownMinutes, aiAgentId, enabled, null, null, null, null);
    }
}
