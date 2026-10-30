package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorJoinResponse;

/**
 * The supervisor is on their way into the call, in {@code mode}: on the Asterisk engine their phone
 * is ringing, on the LiveKit engine the browser joins the call's room.
 *
 * @param supervisorChannelId the channel ringing the supervisor; null on the LiveKit engine
 * @param join                what the browser joins the room with; null on the Asterisk engine
 */
public record MonitorCallResponse(
        String channelId,
        String supervisorChannelId,
        MonitorMode mode,
        OperatorJoinResponse join
) {
}
