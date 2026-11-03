package uz.murodjon.robotcallv2.agent.livekit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiveKit WebRTC and SIP Telephony settings. Bound from {@code voice-agent.telephony.livekit.*}.
 * Used by the LiveKit engine ({@code CompanyConfig.telephonyEngine = LIVEKIT}); see the
 * docker-compose services {@code livekit} and {@code livekit-sip}, and docs/LIVEKIT.md.
 *
 * @param sipTrunkId       the platform company's LiveKit SIP trunk ({@code ST_...}); other
 *                         companies dial their own managed trunk
 * @param publicUrl       the LiveKit address a browser connects to ({@code wss://…}) — where an operator
 *                         joins a transferred call; blank leaves operators unable to take LiveKit transfers
 * @param bridgeUrl        the Go media bridge (docker-compose {@code livekit-bridge})
 * @param answerTimeoutSec how long an outbound call rings before it counts as unanswered
 */
@ConfigurationProperties(prefix = "voice-agent.telephony.livekit")
public record LiveKitProperties(
        boolean enabled,
        String host,
        String apiKey,
        String apiSecret,
        String sipTrunkId,
        String publicUrl,
        String bridgeUrl,
        int answerTimeoutSec
) {
    public LiveKitProperties {
        if (host == null) {
            host = "http://localhost:7880";
        }
        if (bridgeUrl == null) {
            bridgeUrl = "http://localhost:8090";
        }
        if (answerTimeoutSec <= 0) {
            answerTimeoutSec = 30;
        }
    }
}
