package uz.murodjon.robotcallv2.quality.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.quality.domain.entity.ScorecardCriterion;
import uz.murodjon.robotcallv2.quality.infrastructure.persistence.entity.ScorecardCriterionEntity;

@Component
public class ScorecardCriterionEntityMapper {

    public ScorecardCriterion toScorecardCriterion(ScorecardCriterionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ScorecardCriterion(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getInstruction(),
                entity.getWeight(), entity.isEnabled(), entity.getCreatedAt());
    }

    /** Everything an edit may change. */
    public void applyEditableFields(ScorecardCriterionEntity entity, ScorecardCriterion criterion) {
        entity.setName(criterion.name());
        entity.setInstruction(criterion.instruction());
        entity.setWeight(criterion.weight());
        entity.setEnabled(criterion.enabled());
    }
}
