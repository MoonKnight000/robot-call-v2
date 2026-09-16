package uz.murodjon.robotcallv2.inbound.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;

/**
 * DTO → domain for the writes the route port takes. Id, company and creation time are the
 * storage layer's and stay empty here. A {@code ringTimeoutSec} of {@code 0} and a null
 * enum mean "not given": on create the adapter fills its default, on update it keeps what
 * the route had.
 */
@Component
public class InboundRouteMapper {

    public InboundRoute toNewInboundRoute(CreateInboundRouteRequest request) {
        return new InboundRoute(0, 0, request.didNumber(), request.aiAgentId(), request.routeType(),
                request.targetDestination(), request.queueStrategy(), resolveRingTimeoutSec(request.ringTimeoutSec()),
                request.failoverAction(), request.failoverDestination(), request.afterHoursAction(),
                request.afterHoursDestination(), request.ivrMenuConfig(), request.businessHoursStart(),
                request.businessHoursEnd(), request.fallbackMessage(), request.callbackCampaignId(),
                request.scheduleId(), request.callFlowId(), true, null);
    }

    public InboundRoute toUpdatedInboundRoute(UpdateInboundRouteRequest request) {
        return new InboundRoute(0, 0, request.didNumber(), request.aiAgentId(), request.routeType(),
                request.targetDestination(), request.queueStrategy(), resolveRingTimeoutSec(request.ringTimeoutSec()),
                request.failoverAction(), request.failoverDestination(), request.afterHoursAction(),
                request.afterHoursDestination(), request.ivrMenuConfig(), request.businessHoursStart(),
                request.businessHoursEnd(), request.fallbackMessage(), request.callbackCampaignId(),
                request.scheduleId(), request.callFlowId(), request.enabled(), null);
    }

    private static int resolveRingTimeoutSec(Integer ringTimeoutSec) {
        return ringTimeoutSec != null ? ringTimeoutSec : 0;
    }
}
