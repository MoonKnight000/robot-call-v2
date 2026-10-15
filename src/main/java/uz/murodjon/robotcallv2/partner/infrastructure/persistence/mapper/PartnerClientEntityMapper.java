package uz.murodjon.robotcallv2.partner.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClient;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity.PartnerClientEntity;

@Component
public class PartnerClientEntityMapper {

    public PartnerClient toPartnerClient(PartnerClientEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PartnerClient(entity.getPartnerCompanyId(), entity.getClientCompanyId(), entity.getCreatedAt());
    }
}
