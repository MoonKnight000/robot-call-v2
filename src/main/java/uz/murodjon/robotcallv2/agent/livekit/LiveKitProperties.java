package uz.murodjon.robotcallv2.agent.livekit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LiveKit WebRTC and SIP Telephony settings. Bound from {@code voice-agent.telephony.livekit.*}.
 */
@ConfigurationProperties(prefix = "voice-agent.telephony.livekit")
public record LiveKitProperties(
        boolean enabled,
        String host,
        String apiKey,
        String apiSecret,
        String sipTrunkId
) {
    public LiveKitProperties {
        if (host == null) {
            host = "http://localhost:7880";
        }
    }
}
