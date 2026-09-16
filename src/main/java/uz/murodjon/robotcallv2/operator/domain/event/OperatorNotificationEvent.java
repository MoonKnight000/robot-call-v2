package uz.murodjon.robotcallv2.operator.domain.event;

/**
 * Something a supervisor did to a live call from the operator screen — a takeover, a
 * whisper — published as an application event, so the live stream can show it without this
 * feature depending on the live one.
 *
 * @param kind      {@code TAKEOVER} or {@code WHISPER}
 * @param text      what the live panels show
 * @param channelId the call it happened on
 */
public record OperatorNotificationEvent(long companyId, String kind, String text, String channelId) {
}
