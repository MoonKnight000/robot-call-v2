package uz.murodjon.robotcallv2.company.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyConfigRepository;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyConfigEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.mapper.CompanyConfigEntityMapper;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyConfigJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;

@Component
public class CompanyConfigRepositoryAdapter implements CompanyConfigRepository {

    private final CompanyConfigJpaRepository companyConfigJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final CompanyConfigEntityMapper mapper;

    public CompanyConfigRepositoryAdapter(CompanyConfigJpaRepository companyConfigJpaRepository,
                                          CompanyJpaRepository companyJpaRepository,
                                          CompanyConfigEntityMapper mapper) {
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
        CompanyEntity company = companyJpaRepository.getReferenceById(companyId);

        CompanyConfigEntity entity = new CompanyConfigEntity();
        entity.setCompany(company);
        apply(entity, config);
        entity.setCreatedAt(Instant.now());
        return companyConfigJpaRepository.save(entity).getId();
    }

    @Override
    public CompanyConfig find(long companyId) {
        return companyConfigJpaRepository.findByCompanyId(companyId).map(mapper::toCompanyConfig).orElse(null);
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
        entity.setMaxAttemptsPerPhonePerWeek(config.maxAttemptsPerPhonePerWeek());
        entity.setQuietDaysAfterConversation(config.quietDaysAfterConversation());
        entity.setRespectPromisedDate(config.respectPromisedDate());
        entity.setHumanLikePersonaAllowed(config.humanLikePersonaAllowed());
        entity.setHolidays(CompanyConfigEntityMapper.formatHolidays(config.holidays()));
        entity.setScorecardReviewThreshold(config.scorecardReviewThreshold());
        entity.setTelephonyEngine(config.telephonyEngine());
    }
}
