package uz.murodjon.robotcallv2.inbound.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;

@Component
public class InboundRouteMapper {

    public InboundRoute entityToDomain(InboundRouteEntity entity) {
        if (entity == null) {
            return null;
        }
        // ai_agent_id is null for a route that rings a queue, an IVR or a user.
        return new InboundRoute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDidNumber(),
                entity.getAiAgentId(),
                entity.getRouteType(),
                entity.getTargetDestination(),
                entity.getQueueStrategy(),
                entity.getRingTimeoutSec(),
                entity.getFailoverAction(),
                entity.getFailoverDestination(),
                entity.getAfterHoursAction(),
                entity.getAfterHoursDestination(),
                entity.getIvrMenuConfig(),
                entity.getBusinessHoursStart(),
                entity.getBusinessHoursEnd(),
                entity.getFallbackMessage(),
                entity.isEnabled(),
                entity.getCreatedAt()
        );
    }
}
