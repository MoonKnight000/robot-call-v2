package uz.murodjon.robotcallv2.crm.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.crm.application.port.output.CrmSettingsRepository;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmSettings;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmSettingsEntity;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmStatusMappingEntity;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.entity.CrmStatusMappingId;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.mapper.CrmSettingsEntityMapper;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository.CrmSettingsJpaRepository;
import uz.murodjon.robotcallv2.crm.infrastructure.persistence.repository.CrmStatusMappingJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CrmSettingsRepositoryAdapter implements CrmSettingsRepository {

    private final CrmSettingsJpaRepository jpaRepository;
    private final CrmStatusMappingJpaRepository mappingJpaRepository;
    private final CrmSettingsEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public CrmSettingsRepositoryAdapter(CrmSettingsJpaRepository jpaRepository,
                                        CrmStatusMappingJpaRepository mappingJpaRepository,
                                        CrmSettingsEntityMapper mapper,
                                        CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mappingJpaRepository = mappingJpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public Optional<CrmSettings> findByCompanyId(long companyId) {
        Optional<CrmSettingsEntity> settings = jpaRepository.findByCompanyId(companyId);
        List<CrmStatusMappingEntity> mappings = mappingJpaRepository.findByIdCompanyId(companyId);
        if (settings.isEmpty() && mappings.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapper.toCrmSettings(settings.orElse(null), mappings));
    }

    @Override
    @Transactional
    public CrmSettings upsert(long companyId, CrmSettings settings) {
        CrmSettingsEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            CrmSettingsEntity fresh = new CrmSettingsEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            return fresh;
        });
        entity.setAiEmployeeId(settings.aiEmployeeId());
        entity.setTaskTypeId(settings.taskTypeId());
        entity.setUpdatedAt(Instant.now());
        CrmSettingsEntity saved = jpaRepository.save(entity);

        mappingJpaRepository.deleteByIdCompanyId(companyId);
        List<CrmStatusMappingEntity> newMappings = new ArrayList<>();
        if (settings.statusMappings() != null) {
            for (Map.Entry<String, Integer> entry : settings.statusMappings().entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                    newMappings.add(new CrmStatusMappingEntity(
                            new CrmStatusMappingId(companyId, entry.getKey().trim().toUpperCase()),
                            entry.getValue()));
                }
            }
        }
        if (!newMappings.isEmpty()) {
            newMappings = mappingJpaRepository.saveAll(newMappings);
        }

        return mapper.toCrmSettings(saved, newMappings);
    }
}
