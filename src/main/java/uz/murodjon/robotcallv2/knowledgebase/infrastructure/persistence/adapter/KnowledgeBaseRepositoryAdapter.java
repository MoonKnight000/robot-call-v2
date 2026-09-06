package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeBaseRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeItemEntity;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.repository.KnowledgeItemJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class KnowledgeBaseRepositoryAdapter implements KnowledgeBaseRepository {

    private final KnowledgeItemJpaRepository jpaRepository;

    public KnowledgeBaseRepositoryAdapter(KnowledgeItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
        entity.setCompanyId(item.companyId());
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
        return toDomain(saved);
    }

    @Override
    public Optional<KnowledgeItem> findByIdAndCompanyId(long id, long companyId) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<KnowledgeItem> findByCompanyIdAndKey(long companyId, String key) {
        return jpaRepository.findByCompanyIdAndItemKey(companyId, key).map(this::toDomain);
    }

    @Override
    public List<KnowledgeItem> findAllActiveByCompanyId(long companyId) {
        return jpaRepository.findAllByCompanyIdAndActiveTrue(companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Page<KnowledgeItem> findAllByCompanyId(long companyId, String search, Pageable pageable) {
        return jpaRepository.searchByCompany(companyId, search, pageable).map(this::toDomain);
    }

    @Override
    @Transactional
    public void deleteByIdAndCompanyId(long id, long companyId) {
        jpaRepository.deleteByIdAndCompanyId(id, companyId);
    }

    private KnowledgeItem toDomain(KnowledgeItemEntity entity) {
        return new KnowledgeItem(
                entity.getId() != null ? entity.getId() : 0L,
                entity.getCompanyId(),
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
}
