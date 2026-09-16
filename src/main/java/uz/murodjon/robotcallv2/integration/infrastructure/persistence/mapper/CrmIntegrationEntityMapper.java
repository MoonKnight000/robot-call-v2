package uz.murodjon.robotcallv2.integration.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.integration.domain.entity.CrmIntegration;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CrmIntegrationEntity;

@Component
public class CrmIntegrationEntityMapper {

    public CrmIntegration toCrmIntegration(CrmIntegrationEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CrmIntegration(
                entity.getCompanyId(),
                entity.getProvider(),
                entity.getAppName(),
                entity.getGrantsJson(),
                entity.getAccessTokenEnc(),
                entity.getRefreshTokenEnc(),
                entity.getBaseUrlEnc(),
                entity.getTokenExpiresAt(),
                entity.getStatus(),
                entity.getConnectedAt(),
                entity.getCreatedAt()
        );
    }
}
