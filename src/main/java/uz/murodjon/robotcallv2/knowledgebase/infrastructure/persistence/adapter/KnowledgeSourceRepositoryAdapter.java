package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeSourceRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeSourceEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeSourceJpaRepository;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.repository.StoredFileJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class KnowledgeSourceRepositoryAdapter implements KnowledgeSourceRepository {

    private final KnowledgeSourceJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;
    private final StoredFileJpaRepository storedFileJpaRepository;

    public KnowledgeSourceRepositoryAdapter(KnowledgeSourceJpaRepository jpaRepository,
                                            CompanyJpaRepository companyJpaRepository,
                                            AiAgentJpaRepository aiAgentJpaRepository,
                                            StoredFileJpaRepository storedFileJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
        this.storedFileJpaRepository = storedFileJpaRepository;
    }

    @Override
    @Transactional
    public KnowledgeSource save(KnowledgeSource source) {
        KnowledgeSourceEntity entity;
        if (source.id() > 0) {
            entity = jpaRepository.findByCompanyIdAndId(source.companyId(), source.id())
                    .orElseGet(KnowledgeSourceEntity::new);
        } else {
            entity = new KnowledgeSourceEntity();
            entity.setCreatedAt(source.createdAt() != null ? source.createdAt() : Instant.now());
        }

        entity.setCompany(companyJpaRepository.getReferenceById(source.companyId()));
        entity.setAgent(aiAgentReference(source.agentId()));
        entity.setName(source.name());
        entity.setSourceType(source.sourceType());
        entity.setOriginalFileName(source.originalFileName());
        entity.setStoredFile(storedFileReference(source.storedFileId()));
        entity.setChunkCount(source.chunkCount());
        entity.setUrl(source.url());
        entity.setStatus(source.status());
        entity.setErrorCode(source.errorCode());
        entity.setErrorMessage(source.errorMessage());
        entity.setLastIndexedAt(source.lastIndexedAt());

        KnowledgeSourceEntity saved = jpaRepository.save(entity);
        return toKnowledgeSource(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeSource> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(this::toKnowledgeSource);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSource> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(this::toKnowledgeSource)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeSource> findByCompanyIdAndAgentId(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentIdOrderByCreatedAtDesc(companyId, agentId)
                .stream()
                .map(this::toKnowledgeSource)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }

    private KnowledgeSource toKnowledgeSource(KnowledgeSourceEntity entity) {
        return new KnowledgeSource(
                entity.getId(),
                entity.getCompanyId(),
                entity.getAgentId(),
                entity.getName(),
                entity.getSourceType(),
                entity.getOriginalFileName(),
                entity.getStoredFileId(),
                entity.getUrl(),
                entity.getStatus(),
                entity.getErrorCode(),
                entity.getErrorMessage(),
                entity.getLastIndexedAt(),
                entity.getChunkCount(),
                entity.getCreatedAt()
        );
    }

    /** Null for a company-wide source that every agent reads. */
    private AiAgentEntity aiAgentReference(Long id) {
        return id != null ? aiAgentJpaRepository.getReferenceById(id) : null;
    }

    /** Null for a source typed in rather than uploaded. */
    private StoredFileEntity storedFileReference(Long id) {
        return id != null ? storedFileJpaRepository.getReferenceById(id) : null;
    }
}
