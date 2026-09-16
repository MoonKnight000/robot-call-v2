package uz.murodjon.robotcallv2.callrecord.application.dto;

/**
 * The operator's own phone is ringing; when they answer, {@code phone} is dialled.
 *
 * @param operatorChannelId the channel ringing the operator
 */
public record ClickToCallResponse(
        String operatorChannelId,
        String phone
) {
}
