package uz.murodjon.robotcallv2.company.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Component
public class CompanyEntityMapper {

    public Company toCompany(CompanyEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Company(
                entity.getId(),
                entity.getName(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getLogoFileId(),
                entity.getAddress(),
                entity.getMaxConcurrentCalls(),
                entity.getDisabledFeatures(),
                entity.getDisabledIntegrations(),
                entity.getExpiresAt()
        );
    }
}
