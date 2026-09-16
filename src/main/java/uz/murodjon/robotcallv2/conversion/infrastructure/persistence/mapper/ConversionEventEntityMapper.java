package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionEvent;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionEventEntity;

/** Entity ↔ domain for a reported conversion event. */
@Component
public class ConversionEventEntityMapper {

    public ConversionEvent toConversionEvent(ConversionEventEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ConversionEvent(
                entity.getId(),
                entity.getCompanyId(),
                entity.getGoalKey(),
                entity.getPhone(),
                entity.getOccurredAt(),
                entity.getValueUzs(),
                entity.getSource(),
                entity.getEvidence(),
                entity.getDedupeKey(),
                Boolean.TRUE.equals(entity.getRejected()),
                entity.getRejectionReason(),
                entity.getIngestedAt());
    }

    public ConversionEventEntity toConversionEventEntity(ConversionEvent event, CompanyEntity company) {
        if (event == null) {
            return null;
        }
        ConversionEventEntity entity = new ConversionEventEntity();
        entity.setId(event.id());
        entity.setCompany(company);
        entity.setGoalKey(event.goalKey());
        entity.setPhone(event.phone());
        entity.setOccurredAt(event.occurredAt());
        entity.setValueUzs(event.valueUzs());
        entity.setSource(event.source());
        entity.setEvidence(event.evidence());
        entity.setDedupeKey(event.dedupeKey());
        entity.setRejected(event.rejected());
        entity.setRejectionReason(event.rejectionReason());
        entity.setIngestedAt(event.ingestedAt());
        return entity;
    }
}
