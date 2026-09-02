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
        Long scenarioId = entity.getScenario() != null ? entity.getScenario().getId() : null;
        return new InboundRoute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDidNumber(),
                scenarioId,
                entity.getRouteType(),
                entity.getTargetDestination(),
                entity.getQueueStrategy(),
                entity.getRingTimeoutSec(),
                entity.getFailoverAction(),
                entity.getFailoverDestination(),
                entity.getAfterHoursAction(),
                entity.getAfterHoursDestination(),
                entity.getIvrMenuConfig(),
                entity.getLanguage(),
                entity.getBusinessHoursStart(),
                entity.getBusinessHoursEnd(),
                entity.getFallbackMessage(),
                entity.isEnabled(),
                entity.getCreatedAt()
        );
    }
}
