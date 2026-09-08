package uz.murodjon.robotcallv2.conversion.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionGoal;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionGoalEntity;

@Component
public class ConversionGoalMapper {

    public ConversionGoal toConversionGoal(ConversionGoalEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ConversionGoal(
                entity.getId(),
                entity.getCompanyId(),
                entity.getGoalKey(),
                entity.getName(),
                entity.getAttributionWindowHours(),
                entity.getAttributionModel(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public ConversionGoalEntity toEntity(ConversionGoal goal, CompanyEntity company) {
        if (goal == null) {
            return null;
        }
        ConversionGoalEntity entity = new ConversionGoalEntity();
        entity.setId(goal.id());
        entity.setCompany(company);
        entity.setGoalKey(goal.goalKey());
        entity.setName(goal.name());
        entity.setAttributionWindowHours(goal.attributionWindowHours());
        entity.setAttributionModel(goal.attributionModel());
        entity.setEnabled(goal.enabled());
        entity.setCreatedAt(goal.createdAt());
        entity.setUpdatedAt(goal.updatedAt());
        return entity;
    }
}
