package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundRouteEntity;

@Component
public class InboundRouteEntityMapper {

    public InboundRoute toInboundRoute(InboundRouteEntity entity) {
        if (entity == null) {
            return null;
        }
        // ai_agent_id is null for a route that only runs a call flow.
        return new InboundRoute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDidNumber(),
                entity.getAiAgentId(),
                entity.getAfterHoursAction(),
                entity.getAfterHoursDestination(),
                entity.getBusinessHoursStart(),
                entity.getBusinessHoursEnd(),
                entity.getFallbackMessage(),
                entity.getCallbackCampaignId(),
                entity.getScheduleId(),
                entity.getCallFlowId(),
                entity.isEnabled(),
                entity.getCreatedAt()
        );
    }
}
