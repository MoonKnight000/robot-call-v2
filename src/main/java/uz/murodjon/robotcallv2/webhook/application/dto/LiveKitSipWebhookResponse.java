package uz.murodjon.robotcallv2.webhook.application.dto;

/**
 * Dispatch verdict returned to LiveKit SIP Gateway.
 *
 * @param action {@code ACCEPT} or {@code REJECT}
 * @param roomName the LiveKit room to join
 * @param routeId matched inbound route id (if accepted)
 * @param agentId target AI agent id (if routed to agent)
 * @param callFlowId target call flow id (if routed to flow)
 * @param language conversation language (e.g. uz-UZ)
 * @param message rejection or prompt message (if rejected/closed)
 * @param vip whether caller is flagged as VIP
 */
public record LiveKitSipWebhookResponse(
        String action,
        String roomName,
        Long routeId,
        Long agentId,
        Long callFlowId,
        String language,
        String message,
        boolean vip
) {
    public static LiveKitSipWebhookResponse accept(String roomName, Long routeId, Long agentId, Long callFlowId,
                                                   String language, boolean vip) {
        return new LiveKitSipWebhookResponse("ACCEPT", roomName, routeId, agentId, callFlowId, language, null, vip);
    }

    public static LiveKitSipWebhookResponse reject(String message, String language) {
        return new LiveKitSipWebhookResponse("REJECT", null, null, null, null, language, message, false);
    }
}
