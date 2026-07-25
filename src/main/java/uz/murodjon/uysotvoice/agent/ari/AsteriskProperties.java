package uz.murodjon.uysotvoice.agent.ari;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Asterisk ARI connection settings. Bound from {@code voice-agent.asterisk.*}
 * (PROJECT.md §12).
 *
 * @param enabled          when false, the ARI connection is not opened (useful for tests)
 * @param ariUrl           HTTP base URL of Asterisk (e.g. http://localhost:8088/) — ari4java derives the WebSocket
 * @param ariUser          ARI username (ari.conf)
 * @param ariPassword      ARI password (ari.conf)
 * @param appName          Stasis application name registered with Asterisk
 * @param trunkEndpoint    PJSIP endpoint used for outbound calls (pjsip.conf)
 * @param localEndpoint    PJSIP endpoint used for numbers matching {@code localNumberPattern} —
 *                         a registered test softphone. Lets the same originate path be
 *                         exercised for free (dial 600) without burning trunk minutes.
 *                         Blank disables local routing: everything goes to the trunk.
 * @param localNumberPattern regex; a number matching it is dialled via {@code localEndpoint}
 *                         instead of the trunk. Default {@code \d{3,4}} — internal
 *                         extensions are short, real subscriber numbers are not.
 * @param callerId         caller id presented to the callee; blank to leave unset
 * @param answerTimeoutSec seconds to wait for the callee to answer
 */
@ConfigurationProperties(prefix = "voice-agent.asterisk")
public record AsteriskProperties(
        boolean enabled,
        String ariUrl,
        String ariUser,
        String ariPassword,
        String appName,
        String trunkEndpoint,
        String localEndpoint,
        String localNumberPattern,
        String callerId,
        int answerTimeoutSec
) {
}
