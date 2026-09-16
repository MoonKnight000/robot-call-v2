package uz.murodjon.robotcallv2.inbound.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.infrastructure.persistence.entity.InboundCallerRuleEntity;

@Component
public class InboundCallerRuleEntityMapper {

    public InboundCallerRule toInboundCallerRule(InboundCallerRuleEntity entity) {
        return new InboundCallerRule(entity.getId(), entity.getCompanyId(), entity.getPhonePrefix(), entity.getAction(),
                entity.getMessage(), entity.getNote(), entity.getCreatedAt());
    }
}
