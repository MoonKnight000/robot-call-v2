package uz.murodjon.robotcallv2.company.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;

import java.util.List;

@Component
public class CompanyConfigMapper {

    public CompanyConfig entityToDomain(CompanyConfigEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CompanyConfig(
                entity.getId(),
                entity.getCompanyId(),
                entity.getDialWindowStart(),
                entity.getDialWindowEnd(),
                entity.getTimezone(),
                entity.getDefaultLanguage(),
                List.copyOf(entity.getSupportedLanguages()),
                entity.getDisclosureText(),
                entity.getCreatedAt()
        );
    }
}
