package uz.murodjon.robotcallv2.inbound.domain.entity;

import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Which DID answers with what (ROADMAP C.1). Queues, IVR menus and forwards are built as a
 * {@code callFlowId}; a route without one is answered by {@code aiAgentId} in its hours.
 *
 * @param afterHoursAction      what a flowless route does once its hours are over
 * @param afterHoursDestination the night agent's id for {@code AI_AGENT}; null uses {@code aiAgentId}
 * @param callbackCampaignId    the campaign a caller who asks to be called back is added to,
 *                              due at the time they named; null records the request only
 * @param scheduleId            the business schedule whose hours the route keeps; wins over
 *                              {@code businessHoursStart/End}
 * @param callFlowId            the published call flow a call runs; null answers with {@code aiAgentId}
 */
public record InboundRoute(
        long id,
        long companyId,
        String didNumber,
        Long aiAgentId,
        InboundAfterHoursAction afterHoursAction,
        String afterHoursDestination,
        LocalTime businessHoursStart,
        LocalTime businessHoursEnd,
        String fallbackMessage,
        Long callbackCampaignId,
        Long scheduleId,
        Long callFlowId,
        boolean enabled,
        Instant createdAt
) {

    /**
     * A route made from a phone number's own inbound slot, for a number no stored route answers
     * (docs/PHONE-NUMBER-BINDING.md, phase 4): no hours, no after-hours, no callback — the agent
     * or the flow answers whenever the number is called. Never saved; its id is 0.
     */
    public static InboundRoute ofPhoneNumber(long companyId, String phoneNumber, Long aiAgentId, Long callFlowId) {
        return new InboundRoute(0, companyId, phoneNumber, aiAgentId, InboundAfterHoursAction.PLAY_MESSAGE_AND_HANGUP,
                null, null, null, null, null, null, callFlowId, true, null);
    }

    /** The stored route's id, for a row that refers to it; null for a route made from a phone number's slot. */
    public Long storedId() {
        return id > 0 ? id : null;
    }

    /** This route answered by {@code agentId} instead — every other setting is kept. */
    public InboundRoute withAiAgentId(Long agentId) {
        return new InboundRoute(id, companyId, didNumber, agentId, afterHoursAction, afterHoursDestination,
                businessHoursStart, businessHoursEnd, fallbackMessage, callbackCampaignId, scheduleId, callFlowId,
                enabled, createdAt);
    }
}
