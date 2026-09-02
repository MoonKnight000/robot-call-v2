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
 * InboundRoute enriched with scenarioName and OnlinePBX parameters for API response.
 */
public record InboundRouteRow(
        long id,
        String didNumber,
        Long scenarioId,
        String scenarioName,
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
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime businessHoursEnd,
        String fallbackMessage,
        boolean enabled,
        Instant createdAt
) {

    public static InboundRouteRow of(InboundRoute r, String scenarioName) {
        return new InboundRouteRow(
                r.id(),
                r.didNumber(),
                r.scenarioId(),
                scenarioName,
                r.routeType(),
                r.targetDestination(),
                r.queueStrategy(),
                r.ringTimeoutSec(),
                r.failoverAction(),
                r.failoverDestination(),
                r.afterHoursAction(),
                r.afterHoursDestination(),
                r.ivrMenuConfig(),
                r.language(),
                r.businessHoursStart(),
                r.businessHoursEnd(),
                r.fallbackMessage(),
                r.enabled(),
                r.createdAt()
        );
    }
}
