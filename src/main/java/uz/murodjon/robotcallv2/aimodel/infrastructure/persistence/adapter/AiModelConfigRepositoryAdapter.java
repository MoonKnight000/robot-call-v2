package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelConfigRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelConfigEntity;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.mapper.AiModelConfigEntityMapper;
import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.repository.AiModelConfigJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;

import java.time.Instant;

@Component
public class AiModelConfigRepositoryAdapter implements AiModelConfigRepository {

    private final AiModelConfigJpaRepository jpaRepository;
    private final AiModelConfigEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public AiModelConfigRepositoryAdapter(AiModelConfigJpaRepository jpaRepository, AiModelConfigEntityMapper mapper,
                                          CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public AiModelConfig findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId)
                .map(mapper::toAiModelConfig)
                .orElse(null);
    }

    @Override
    public AiModelConfig upsert(long companyId, AiModelConfig config) {
        AiModelConfigEntity entity = findOrCreate(companyId);
        entity.setModel(config.model());
        entity.setTemperature(config.temperature());
        entity.setMaxOutputTokens(config.maxOutputTokens());
        entity.setMaxCallSeconds(config.maxCallSeconds());
        entity.setMaxTokensPerCall(config.maxTokensPerCall());
        return mapper.toAiModelConfig(jpaRepository.save(entity));
    }

    @Override
    public AiModelConfig updateOwnLlm(long companyId, OwnLlmEndpoint ownLlm) {
        AiModelConfigEntity entity = findOrCreate(companyId);
        entity.setOwnLlmBaseUrl(ownLlm == null ? null : ownLlm.baseUrl());
        entity.setOwnLlmModel(ownLlm == null ? null : ownLlm.model());
        entity.setOwnLlmFastModel(ownLlm == null ? null : ownLlm.fastModel());
        entity.setOwnLlmApiKeySecret(ownLlm == null ? null : ownLlm.apiKeySecret());
        return mapper.toAiModelConfig(jpaRepository.save(entity));
    }

    private AiModelConfigEntity findOrCreate(long companyId) {
        return jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            AiModelConfigEntity fresh = new AiModelConfigEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
    }
}
