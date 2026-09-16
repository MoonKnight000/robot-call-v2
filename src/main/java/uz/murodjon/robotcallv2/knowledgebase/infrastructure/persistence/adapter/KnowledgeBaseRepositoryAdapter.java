package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemFilter;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeItemEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.mapper.KnowledgeItemEntityMapper;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeItemJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.util.LikePatterns;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class KnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {

    private final KnowledgeItemJpaRepository jpaRepository;
    private final KnowledgeItemEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;

    public KnowledgeBaseRepositoryAdapter(KnowledgeItemJpaRepository jpaRepository,
                                          KnowledgeItemEntityMapper mapper,
                                          CompanyJpaRepository companyJpaRepository,
                                          AiAgentJpaRepository aiAgentJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
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
        mapper.applyToEntity(item, entity);
        entity.setUpdatedAt(Instant.now());

        KnowledgeItemEntity saved = jpaRepository.save(entity);
        return mapper.toKnowledgeItem(saved);
    }

    @Override
    public Optional<KnowledgeItem> findByIdAndCompanyId(long id, long companyId) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toKnowledgeItem);
    }

    @Override
    public Optional<KnowledgeItem> findByCompanyIdAndKey(long companyId, String key) {
        return jpaRepository.findByCompanyIdAndItemKey(companyId, key).map(mapper::toKnowledgeItem);
    }

    @Override
    public List<KnowledgeItem> findAllActiveByCompanyIdAndAgentId(long companyId, Long agentId) {
        return jpaRepository.findAllActiveByCompanyIdAndAgentId(companyId, agentId).stream()
                .map(mapper::toKnowledgeItem)
                .toList();
    }

    @Override
    public PageableData<KnowledgeItem> findPageByCompanyId(long companyId, KnowledgeItemFilter filter) {
        // The page carries its total; the separate count query used to run it a second time.
        Page<KnowledgeItemEntity> page = jpaRepository.findAll(searchSpecification(companyId, filter),
                filter.pageable());
        return PageableData.of(page.getContent().stream().map(mapper::toKnowledgeItem).toList(),
                filter.pageable().getPageNumber(), filter.pageable().getPageSize(), page.getTotalElements());
    }

    /**
     * The company's items, narrowed to one agent's when the filter names it, whose key,
     * title, keywords or topic contain the search text. The LIKE pattern is built once here
     * rather than with CONCAT per row; a blank search does not narrow.
     */
    private static Specification<KnowledgeItemEntity> searchSpecification(long companyId, KnowledgeItemFilter filter) {
        String pattern = filter.search() == null || filter.search().isBlank()
                ? null
                : LikePatterns.buildContainsPattern(filter.search().toLowerCase());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (filter.agentId() != null) {
                predicates.add(cb.equal(root.get("agent").get("id"), filter.agentId()));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("itemKey")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("title")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("keywords")), pattern, LikePatterns.ESCAPE),
                        cb.like(cb.lower(root.get("topic")), pattern, LikePatterns.ESCAPE)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional
    public void deleteByIdAndCompanyId(long id, long companyId) {
        jpaRepository.deleteByIdAndCompanyId(id, companyId);
    }

    /** Null for a company-wide item that every agent reads. */
    private AiAgentEntity aiAgentReference(Long id) {
        return id != null ? aiAgentJpaRepository.getReferenceById(id) : null;
    }
}
