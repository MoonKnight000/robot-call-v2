package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.mapper.CompanyConfigMapper;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyConfigJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

@Component
public class CompanyConfigRepositoryAdapter implements CompanyConfigRepository {

    private final CompanyConfigJpaRepository jpa;
    private final CompanyJpaRepository companyJpa;
    private final CompanyConfigMapper mapper;

    public CompanyConfigRepositoryAdapter(CompanyConfigJpaRepository jpa,
                                          CompanyJpaRepository companyJpa,
                                          CompanyConfigMapper mapper) {
        this.jpa = jpa;
        this.companyJpa = companyJpa;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                       Language defaultLanguage, List<Language> supportedLanguages, String disclosureText) {
        CompanyEntity company = companyJpa.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        CompanyConfigEntity entity = new CompanyConfigEntity();
        entity.setCompany(company);
        entity.setDialWindowStart(dialWindowStart);
        entity.setDialWindowEnd(dialWindowEnd);
        entity.setTimezone(timezone);
        entity.setDefaultLanguage(defaultLanguage);
        entity.setSupportedLanguages(supportedLanguages);
        entity.setDisclosureText(disclosureText);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    @Override
    public CompanyConfig find(long companyId) {
        return jpa.findByCompanyId(companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public void update(long companyId, LocalTime dialWindowStart, LocalTime dialWindowEnd, String timezone,
                       Language defaultLanguage, List<Language> supportedLanguages, String disclosureText) {
        jpa.findByCompanyId(companyId).ifPresent(entity -> {
            entity.setDialWindowStart(dialWindowStart);
            entity.setDialWindowEnd(dialWindowEnd);
            entity.setTimezone(timezone);
            entity.setDefaultLanguage(defaultLanguage);
            entity.setSupportedLanguages(supportedLanguages);
            entity.setDisclosureText(disclosureText);
            jpa.save(entity);
        });
    }
}
