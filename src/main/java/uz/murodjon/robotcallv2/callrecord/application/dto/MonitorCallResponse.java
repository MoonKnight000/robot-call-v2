package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.callrecord.domain.enums.MonitorMode;

/**
 * The supervisor's phone is ringing; once answered they are on the call in {@code mode}.
 *
 * @param supervisorChannelId the channel ringing the supervisor
 */
public record MonitorCallResponse(
        String channelId,
        String supervisorChannelId,
        MonitorMode mode
) {
}
