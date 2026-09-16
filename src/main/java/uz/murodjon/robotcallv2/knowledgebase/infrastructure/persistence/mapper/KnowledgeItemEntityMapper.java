package uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItem;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.persistence.entity.KnowledgeItemEntity;

/** Maps between {@link KnowledgeItemEntity} and {@link KnowledgeItem}. */
@Component
public class KnowledgeItemEntityMapper {

    public KnowledgeItem toKnowledgeItem(KnowledgeItemEntity entity) {
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

    /** The item's own fields; the company and agent references are the adapter's to resolve. */
    public void applyToEntity(KnowledgeItem item, KnowledgeItemEntity entity) {
        entity.setItemKey(item.key());
        entity.setTopic(item.topic());
        entity.setTitle(item.title());
        entity.setAnswerUz(item.answerUz());
        entity.setAnswerRu(item.answerRu());
        entity.setAnswerEn(item.answerEn());
        entity.setKeywords(item.keywords() != null ? item.keywords() : "");
        entity.setActive(item.active());
    }
}
