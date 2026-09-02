package uz.murodjon.robotcallv2.inbound.domain.entity;

import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundFailoverAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteType;
import uz.murodjon.robotcallv2.inbound.domain.enums.QueueStrategy;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Domain model of inbound_route with OnlinePBX / Virtual ATS capabilities (ROADMAP C.1).
 */
public record InboundRoute(
        long id,
        long companyId,
        String didNumber,
        Long scenarioId,
        InboundRouteType routeType,
        String targetDestination,
        QueueStrategy queueStrategy,
        int ringTimeoutSec,
        InboundFailoverAction failoverAction,
        String failoverDestination,
        InboundAfterHoursAction afterHoursAction,
        String afterHoursDestination,
        String ivrMenuConfig,
        String language,
        LocalTime businessHoursStart,
        LocalTime businessHoursEnd,
        String fallbackMessage,
        boolean enabled,
        Instant createdAt
) {
}
