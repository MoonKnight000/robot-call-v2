package uz.murodjon.robotcallv2.inbound.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.inbound.application.dto.CreateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.application.dto.UpdateInboundRouteRequest;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;

/**
 * DTO → domain for the writes the route port takes. Id, company and creation time are the
 * storage layer's and stay empty here. A null {@code afterHoursAction} means "not given": on
 * create the adapter fills its default, on update it keeps what the route had.
 */
@Component
public class InboundRouteMapper {

    public InboundRoute toNewInboundRoute(CreateInboundRouteRequest request) {
        return new InboundRoute(0, 0, request.didNumber(), request.aiAgentId(), request.afterHoursAction(),
                request.afterHoursDestination(), request.businessHoursStart(), request.businessHoursEnd(),
                request.fallbackMessage(), request.callbackCampaignId(), request.scheduleId(), request.callFlowId(),
                true, null);
    }

    public InboundRoute toUpdatedInboundRoute(UpdateInboundRouteRequest request) {
        return new InboundRoute(0, 0, request.didNumber(), request.aiAgentId(), request.afterHoursAction(),
                request.afterHoursDestination(), request.businessHoursStart(), request.businessHoursEnd(),
                request.fallbackMessage(), request.callbackCampaignId(), request.scheduleId(), request.callFlowId(),
                request.enabled(), null);
    }
}
