package uz.murodjon.robotcallv2.webhook.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * {@code livekit.ParticipantInfo} as it arrives in a webhook. A phone leg has
 * {@code kind = SIP} and carries the numbers in its attributes: {@code sip.phoneNumber}
 * (the caller / callee) and {@code sip.trunkPhoneNumber} (our DID).
 *
 * @param disconnectReason set on {@code participant_left}, e.g. {@code USER_UNAVAILABLE}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveKitParticipantInfo(
        String identity,
        String kind,
        Map<String, String> attributes,
        @JsonAlias("disconnect_reason")
        String disconnectReason
) {
    public boolean isSip() {
        return "SIP".equals(kind);
    }

    public String findAttribute(String name) {
        String value = attributes != null ? attributes.get(name) : null;
        return value != null ? value : "";
    }
}
