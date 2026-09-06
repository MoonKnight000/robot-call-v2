package uz.murodjon.robotcallv2.callrecord.application.dto;

/**
 * Everything the browser needs to dial in: connect a SIP-over-WebSocket client to
 * {@code wsUrl} as {@code sipUser}/{@code sipPassword} and INVITE {@code dialNumber}
 * with the header {@code sessionHeader: sessionId}.
 */
public record WebTestCallResponse(
        String sessionId,
        String wsUrl,
        String sipUser,
        String sipPassword,
        String dialNumber,
        String sessionHeader
) {
}
