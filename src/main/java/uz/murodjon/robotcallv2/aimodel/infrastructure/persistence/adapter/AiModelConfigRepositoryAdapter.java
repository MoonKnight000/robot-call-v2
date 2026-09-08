package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aimodel.application.mapper.AiModelConfigMapper;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelConfigRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelConfigEntity;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.repository.AiModelConfigJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;

@Component
public class AiModelConfigRepositoryAdapter implements AiModelConfigRepository {

    private final AiModelConfigJpaRepository jpaRepository;
    private final AiModelConfigMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public AiModelConfigRepositoryAdapter(AiModelConfigJpaRepository jpaRepository, AiModelConfigMapper mapper,
                                          CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public AiModelConfig findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId)
                .map(mapper::entityToDomain)
                .orElse(null);
    }

    @Override
    public AiModelConfig upsert(long companyId, AiModelConfig config) {
        AiModelConfigEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            AiModelConfigEntity fresh = new AiModelConfigEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setModel(config.model());
        entity.setTemperature(config.temperature());
        entity.setMaxOutputTokens(config.maxOutputTokens());
        entity.setMaxCallSeconds(config.maxCallSeconds());
        entity.setMaxTokensPerCall(config.maxTokensPerCall());
        return mapper.entityToDomain(jpaRepository.save(entity));
    }
}
