package uz.murodjon.robotcallv2.inbound.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundAfterHoursAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundFailoverAction;
import uz.murodjon.robotcallv2.inbound.domain.enums.InboundRouteType;
import uz.murodjon.robotcallv2.inbound.domain.enums.QueueStrategy;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.Instant;
import java.time.LocalTime;

/**
 * InboundRoute enriched with the agent's name and OnlinePBX parameters for API response.
 */
public record InboundRouteRow(
        long id,
        String didNumber,
        Long aiAgentId,
        String aiAgentName,
        InboundRouteType routeType,
        String targetDestination,
        QueueStrategy queueStrategy,
        int ringTimeoutSec,
        InboundFailoverAction failoverAction,
        String failoverDestination,
        InboundAfterHoursAction afterHoursAction,
        String afterHoursDestination,
        String ivrMenuConfig,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursEnd,
        String fallbackMessage,
        Long callbackCampaignId,
        Long scheduleId,
        Long callFlowId,
        boolean enabled,
        Instant createdAt
) {

    public static InboundRouteRow of(InboundRoute route, String aiAgentName) {
        return new InboundRouteRow(
                route.id(),
                route.didNumber(),
                route.aiAgentId(),
                aiAgentName,
                route.routeType(),
                route.targetDestination(),
                route.queueStrategy(),
                route.ringTimeoutSec(),
                route.failoverAction(),
                route.failoverDestination(),
                route.afterHoursAction(),
                route.afterHoursDestination(),
                route.ivrMenuConfig(),
                route.businessHoursStart(),
                route.businessHoursEnd(),
                route.fallbackMessage(),
                route.callbackCampaignId(),
                route.scheduleId(),
                route.callFlowId(),
                route.enabled(),
                route.createdAt()
        );
    }
}
