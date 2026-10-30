package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.operator.application.dto.OperatorJoinResponse;

/**
 * Everything the browser needs to start the call. On the Asterisk engine: connect a SIP-over-WebSocket
 * client to {@code wsUrl} as {@code sipUser}/{@code sipPassword} and INVITE {@code dialNumber} with the
 * header {@code sessionHeader: sessionId}. On the LiveKit engine those five are null and the browser joins
 * the call's room with {@code join}.
 *
 * @param join {@code {url, token, identity}} for {@code livekit-client}; null on the Asterisk engine
 */
public record WebTestCallResponse(
        String sessionId,
        String wsUrl,
        String sipUser,
        String sipPassword,
        String dialNumber,
        String sessionHeader,
        OperatorJoinResponse join
) {
}
