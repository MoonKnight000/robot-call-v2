package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.conversion.domain.entity.ConversionAttribution;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionAttributionEntity;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionEventEntity;

/** Entity ↔ domain for what a conversion event was attributed to. */
@Component
public class ConversionAttributionEntityMapper {

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
    public ConversionAttributionEntity toConversionAttributionEntity(ConversionAttribution attribution,
                                                                    CompanyEntity company,
                                                                    ConversionEventEntity conversionEvent,
                                                                    CampaignEntity campaign,
                                                                    CampaignVariantEntity variant,
                                                                    CallAttemptEntity callAttempt) {
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
