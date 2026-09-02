package uz.murodjon.robotcallv2.siptrunk.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.SipTrunkEntity;

@Component
public class SipTrunkMapper {

    public SipTrunk entityToDomain(SipTrunkEntity entity) {
        if (entity == null) {
            return null;
        }
        return new SipTrunk(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getPjsipEndpoint(),
                entity.getCallerId(),
                entity.getHost(),
                entity.getPort(),
                entity.getSipUsername(),
                entity.getSipPasswordEnc(),
                entity.getTransport(),
                entity.getCodecs(),
                entity.isDefault(),
                entity.isEnabled(),
                entity.getCreatedAt()
        );
    }
}
