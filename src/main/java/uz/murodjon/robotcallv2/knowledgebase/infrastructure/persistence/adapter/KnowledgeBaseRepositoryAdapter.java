package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeItemEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeItemJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class KnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {

    private final KnowledgeItemJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;

    public KnowledgeBaseRepositoryAdapter(KnowledgeItemJpaRepository jpaRepository,
                                          CompanyJpaRepository companyJpaRepository,
                                          AiAgentJpaRepository aiAgentJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
    }

    @Override
    @Transactional
    public KnowledgeItem save(KnowledgeItem item) {
        KnowledgeItemEntity entity;
        if (item.id() > 0) {
            entity = jpaRepository.findByIdAndCompanyId(item.id(), item.companyId())
                    .orElse(new KnowledgeItemEntity());
        } else {
            entity = new KnowledgeItemEntity();
        }
        entity.setCompany(companyJpaRepository.getReferenceById(item.companyId()));
        entity.setAgent(aiAgentReference(item.agentId()));
        entity.setItemKey(item.key());
        entity.setTopic(item.topic());
        entity.setTitle(item.title());
        entity.setAnswerUz(item.answerUz());
        entity.setAnswerRu(item.answerRu());
        entity.setAnswerEn(item.answerEn());
        entity.setKeywords(item.keywords() != null ? item.keywords() : "");
        entity.setActive(item.active());
        entity.setUpdatedAt(Instant.now());

        KnowledgeItemEntity saved = jpaRepository.save(entity);
        return toKnowledgeItem(saved);
    }

    @Override
    public Optional<KnowledgeItem> findByIdAndCompanyId(long id, long companyId) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(this::toKnowledgeItem);
    }

    @Override
    public Optional<KnowledgeItem> findByCompanyIdAndKey(long companyId, String key) {
        return jpaRepository.findByCompanyIdAndItemKey(companyId, key).map(this::toKnowledgeItem);
    }

    @Override
    public List<KnowledgeItem> findAllActiveByCompanyIdAndAgentId(long companyId, Long agentId) {
        return jpaRepository.findAllActiveByCompanyIdAndAgentId(companyId, agentId).stream()
                .map(this::toKnowledgeItem)
                .toList();
    }

    @Override
    public List<KnowledgeItem> findAllByCompanyId(long companyId, KnowledgeItemFilter filter) {
        return jpaRepository.searchByCompany(companyId, filter.agentId(), searchPattern(filter), filter.pageable())
                .stream()
                .map(this::toKnowledgeItem)
                .toList();
    }

    @Override
    public long countByCompanyId(long companyId, KnowledgeItemFilter filter) {
        return jpaRepository.countSearchByCompany(companyId, filter.agentId(), searchPattern(filter));
    }

    /**
     * The query compares against a ready LIKE pattern rather than building one with
     * CONCAT, so the lowercasing and the wildcards happen once here instead of per row.
     * A blank search is passed as null, which the query reads as "do not narrow".
     */
    private static String searchPattern(KnowledgeItemFilter filter) {
        if (filter.search() == null || filter.search().isBlank()) {
            return null;
        }
        return "%" + filter.search().trim().toLowerCase() + "%";
    }

    @Override
    @Transactional
    public void deleteByIdAndCompanyId(long id, long companyId) {
        jpaRepository.deleteByIdAndCompanyId(id, companyId);
    }

    private KnowledgeItem toKnowledgeItem(KnowledgeItemEntity entity) {
        return new KnowledgeItem(
                entity.getId() != null ? entity.getId() : 0L,
                entity.getCompanyId(),
                entity.getAgentId(),
                entity.getItemKey(),
                entity.getTopic(),
                entity.getTitle(),
                entity.getAnswerUz(),
                entity.getAnswerRu(),
                entity.getAnswerEn(),
                entity.getKeywords(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /** Null for a company-wide item that every agent reads. */
    private AiAgentEntity aiAgentReference(Long id) {
        return id != null ? aiAgentJpaRepository.getReferenceById(id) : null;
    }
}
