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
 * @param requireAcceptance on a transfer without a queue, whether the operator who answers
 *                          has to press 1 after the briefing before the caller is connected —
 *                          a mobile's voicemail or a busy colleague otherwise takes the caller
 * @param acceptTimeoutSec  how long that 1 is waited for; 10 when not set
 */
@ConfigurationProperties(prefix = "voice-agent.operator")
public record OperatorProperties(
        boolean enabled,
        String endpoint,
        int answerTimeoutSec,
        String sipConfigFileName,
        boolean requireAcceptance,
        int acceptTimeoutSec
) {
    public OperatorProperties {
        if (sipConfigFileName == null || sipConfigFileName.isBlank()) {
            sipConfigFileName = "pjsip_operators.conf";
        }
        if (acceptTimeoutSec <= 0) {
            acceptTimeoutSec = 10;
        }
    }
}
