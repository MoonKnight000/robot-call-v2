package uz.murodjon.robotcallv2.conversion.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionAttribution;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionEvent;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionAttributionEntity;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionEventEntity;

/** Both halves of a conversion: the reported event and what it was attributed to. */
@Component
public class ConversionMapper {

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

    public ConversionEventEntity toEntity(ConversionEvent event, CompanyEntity company) {
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

    public ConversionAttribution toConversionAttribution(ConversionAttributionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ConversionAttribution(
                entity.getId(),
                entity.getConversionEventId(),
                entity.getCompanyId(),
                entity.getCampaignId(),
                entity.getVariantId(),
                entity.getCallAttemptId(),
                entity.getAttributionModel(),
                entity.getWindowHours(),
                entity.getAttributedValueUzs(),
                entity.getComputedAt());
    }

    /** Campaign, variant and call are null when the conversion could not be traced back to one. */
    public ConversionAttributionEntity toEntity(ConversionAttribution attribution, CompanyEntity company,
                                                ConversionEventEntity conversionEvent, CampaignEntity campaign,
                                                CampaignVariantEntity variant, CallAttemptEntity callAttempt) {
        if (attribution == null) {
            return null;
        }
        ConversionAttributionEntity entity = new ConversionAttributionEntity();
        entity.setId(attribution.id());
        entity.setConversionEvent(conversionEvent);
        entity.setCompany(company);
        entity.setCampaign(campaign);
        entity.setVariant(variant);
        entity.setCallAttempt(callAttempt);
        entity.setAttributionModel(attribution.attributionModel());
        entity.setWindowHours(attribution.windowHours());
        entity.setAttributedValueUzs(attribution.attributedValueUzs());
        entity.setComputedAt(attribution.computedAt());
        return entity;
    }
}
