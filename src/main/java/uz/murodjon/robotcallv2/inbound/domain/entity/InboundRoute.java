package uz.murodjon.robotcallv2.inbound.domain.entity;

import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundFailoverAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteType;
import uz.murodjon.robotcallv2.inbound.domain.enums.QueueStrategy;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Domain model of inbound_route with OnlinePBX / Virtual ATS capabilities (ROADMAP C.1).
 *
 * @param callbackCampaignId the campaign a caller who asks to be called back is added to,
 *                           due at the time they named; null records the request only
 * @param scheduleId         the business schedule whose hours the route keeps; wins over
 *                           {@code businessHoursStart/End}
 * @param callFlowId         the published call flow a call runs; null answers with {@code aiAgentId}
 */
public record InboundRoute(
        long id,
        long companyId,
        String didNumber,
        Long aiAgentId,
        InboundRouteType routeType,
        String targetDestination,
        QueueStrategy queueStrategy,
        int ringTimeoutSec,
        InboundFailoverAction failoverAction,
        String failoverDestination,
        InboundAfterHoursAction afterHoursAction,
        String afterHoursDestination,
        String ivrMenuConfig,
        LocalTime businessHoursStart,
        LocalTime businessHoursEnd,
        String fallbackMessage,
        Long callbackCampaignId,
        Long scheduleId,
        Long callFlowId,
        boolean enabled,
        Instant createdAt
) {
}
