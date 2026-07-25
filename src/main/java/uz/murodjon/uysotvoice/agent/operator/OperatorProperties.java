package uz.murodjon.uysotvoice.agent.operator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Human-operator transfer settings. Bound from {@code voice-agent.operator.*}
 * (PROJECT.md §10 Bosqich 11, §11.6).
 *
 * @param enabled          when false, a transfer request just hangs up
 * @param endpoint         channel to originate for the operator — a direct agent
 *                         ({@code PJSIP/operator}) or a queue via a Local channel
 *                         ({@code Local/support@operators})
 * @param answerTimeoutSec seconds to wait for the operator/queue to answer
 */
@ConfigurationProperties(prefix = "voice-agent.operator")
public record OperatorProperties(
        boolean enabled,
        String endpoint,
        int answerTimeoutSec
) {
}
