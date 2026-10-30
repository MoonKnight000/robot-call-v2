package uz.murodjon.robotcallv2.webhook.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code livekit.Room} as it arrives in a webhook. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveKitRoomInfo(
        String name
) {
}
