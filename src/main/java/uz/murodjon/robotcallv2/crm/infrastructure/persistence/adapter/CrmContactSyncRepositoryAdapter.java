package uz.murodjon.robotcallv2.crm.infrastructure.persistence.adapter;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmContactSyncRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncSettings;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmContactSyncState;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmContactSyncEntity;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.mapper.CrmContactSyncEntityMapper;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository.CrmContactSyncJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CrmContactSyncRepositoryAdapter implements CrmContactSyncRepository {

    private final CrmContactSyncJpaRepository crmContactSyncJpaRepository;
    private final CrmContactSyncEntityMapper mapper;

    public CrmContactSyncRepositoryAdapter(CrmContactSyncJpaRepository crmContactSyncJpaRepository,
                                           CrmContactSyncEntityMapper mapper) {
        this.crmContactSyncJpaRepository = crmContactSyncJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CrmContactSyncSettings findSettings(long companyId) {
        return crmContactSyncJpaRepository.findById(companyId).map(mapper::toCrmContactSyncSettings).orElse(null);
    }

    @Override
    public CrmContactSyncState findState(long companyId) {
        return crmContactSyncJpaRepository.findById(companyId).map(mapper::toCrmContactSyncState)
                .orElseGet(CrmContactSyncState::empty);
    }

    @Override
    public void saveSettings(long companyId, CrmContactSyncSettings settings) {
        CrmContactSyncEntity entity = crmContactSyncJpaRepository.findById(companyId).orElseGet(() -> {
            CrmContactSyncEntity created = new CrmContactSyncEntity();
            created.setCompanyId(companyId);
            return created;
        });
        entity.setEnabled(settings.enabled());
        entity.setFieldMappings(settings.fieldMappings());
        entity.setPushMappings(settings.pushMappings());
        entity.setUpdatedAt(Instant.now());
        crmContactSyncJpaRepository.save(entity);
    }

    @Override
    public void saveState(long companyId, CrmContactSyncState state) {
        crmContactSyncJpaRepository.findById(companyId).ifPresent(entity -> {
            mapper.copyState(state, entity);
            crmContactSyncJpaRepository.save(entity);
        });
    }

    @Override
    public List<Long> findDueCompanyIds(Instant now, int limit) {
        return crmContactSyncJpaRepository.findDueCompanyIds(now, PageRequest.of(0, limit));
    }
}
