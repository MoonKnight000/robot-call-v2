package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Map;

/**
 * A campaign's per-disposition retry rules ({@code campaign.retry_policy}). A disposition
 * with no rule falls back to the campaign's {@code retryIntervalMinutes} and then to the
 * deployment's per-disposition defaults ({@code RetrySchedule}).
 */
public record RetryPolicy(Map<Disposition, RetryRule> rules) {

    public static final RetryPolicy NONE = new RetryPolicy(Map.of());

    public RetryPolicy {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    /** The rule for {@code disposition}, or null when the policy says nothing about it. */
    public RetryRule ruleFor(Disposition disposition) {
        return disposition == null ? null : rules.get(disposition);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
