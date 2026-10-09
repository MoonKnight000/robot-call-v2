package uz.murodjon.robotcallv2.dialer.application.dto;

/**
 * A preview card being dialled ({@code POST .../targets/{targetId}/dial}): the operator's
 * phone rings first, and the client is dialled once they pick up.
 *
 * @param operatorChannelId the channel ringing the operator
 */
public record PreviewDialResponse(long targetId, String operatorChannelId) {
}
