package uz.murodjon.robotcallv2.crm.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmSettingsRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmSettingsEntity;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.mapper.CrmSettingsEntityMapper;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository.CrmSettingsJpaRepository;

import java.time.Instant;
import java.util.Optional;

@Component
public class CrmSettingsRepositoryAdapter implements CrmSettingsRepository {

    private final CrmSettingsJpaRepository jpaRepository;
    private final CrmSettingsEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public CrmSettingsRepositoryAdapter(CrmSettingsJpaRepository jpaRepository, CrmSettingsEntityMapper mapper,
                                        CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public Optional<CrmSettings> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(mapper::toCrmSettings);
    }

    @Override
    public CrmSettings upsert(long companyId, CrmSettings settings) {
        CrmSettingsEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            CrmSettingsEntity fresh = new CrmSettingsEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            return fresh;
        });
        entity.setAiEmployeeId(settings.aiEmployeeId());
        entity.setMeetingBookedStatusId(settings.meetingBookedStatusId());
        entity.setPromiseToPayStatusId(settings.promiseToPayStatusId());
        entity.setNotInterestedStatusId(settings.notInterestedStatusId());
        entity.setCallbackStatusId(settings.callbackStatusId());
        entity.setNewLeadStatusId(settings.newLeadStatusId());
        entity.setTaskTypeId(settings.taskTypeId());
        entity.setUpdatedAt(Instant.now());
        return mapper.toCrmSettings(jpaRepository.save(entity));
    }
}
