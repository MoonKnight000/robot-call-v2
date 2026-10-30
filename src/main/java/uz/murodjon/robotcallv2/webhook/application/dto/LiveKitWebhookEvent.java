package uz.murodjon.robotcallv2.webhook.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A LiveKit server webhook ({@code livekit.WebhookEvent}, protobuf JSON). Only the fields
 * this backend reads; the rest is ignored.
 *
 * @param event e.g. {@code participant_joined}, {@code participant_left}, {@code room_finished}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveKitWebhookEvent(
        String event,
        LiveKitRoomInfo room,
        LiveKitParticipantInfo participant
) {
}
