package uz.murodjon.robotcallv2.operator.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Human-operator transfer settings. Bound from {@code voice-agent.operator.*}
 * (PROJECT.md §10 Bosqich 11, §11.6).
 *
 * @param enabled           when false, a transfer request just hangs up
 * @param endpoint          channel to originate for the operator when the agent names no
 *                          queue — a direct agent ({@code PJSIP/operator}) or a queue via a
 *                          Local channel ({@code Local/support@operators})
 * @param answerTimeoutSec  seconds to wait for that endpoint to answer
 * @param sipConfigFileName the generated file holding the operators' own PJSIP endpoints,
 *                          written into {@code voice-agent.siptrunk.config-dir}
 */
@ConfigurationProperties(prefix = "voice-agent.operator")
public record OperatorProperties(
        boolean enabled,
        String endpoint,
        int answerTimeoutSec,
        String sipConfigFileName
) {
    public OperatorProperties {
        if (sipConfigFileName == null || sipConfigFileName.isBlank()) {
            sipConfigFileName = "pjsip_operators.conf";
        }
    }
}
