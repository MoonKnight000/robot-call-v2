package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.mapper.CompanyConfigMapper;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyConfigJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;

@Component
public class CompanyConfigRepositoryAdapter implements CompanyConfigRepository {

    private final CompanyConfigJpaRepository companyConfigJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CompanyConfigMapper mapper;

    public CompanyConfigRepositoryAdapter(CompanyConfigJpaRepository companyConfigJpaRepository,
                                          CompanyJpaRepository companyJpaRepository,
                                          CompanyConfigMapper mapper) {
        this.companyConfigJpaRepository = companyConfigJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByCompanyId(long companyId) {
        return companyConfigJpaRepository.findByCompanyId(companyId).isPresent();
    }

    @Override
    public long create(long companyId, CompanyConfig config) {
        CompanyEntity company = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        CompanyConfigEntity entity = new CompanyConfigEntity();
        entity.setCompany(company);
        apply(entity, config);
        entity.setCreatedAt(Instant.now());
        return companyConfigJpaRepository.save(entity).getId();
    }

    @Override
    public CompanyConfig find(long companyId) {
        return companyConfigJpaRepository.findByCompanyId(companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public void update(long companyId, CompanyConfig config) {
        companyConfigJpaRepository.findByCompanyId(companyId).ifPresent(entity -> {
            apply(entity, config);
            companyConfigJpaRepository.save(entity);
        });
    }

    private static void apply(CompanyConfigEntity entity, CompanyConfig config) {
        entity.setDialWindowStart(config.dialWindowStart());
        entity.setDialWindowEnd(config.dialWindowEnd());
        entity.setTimezone(config.timezone());
        entity.setDefaultLanguage(config.defaultLanguage());
        entity.setSupportedLanguages(config.supportedLanguages());
        entity.setDisclosureText(config.disclosureText());
    }
}
