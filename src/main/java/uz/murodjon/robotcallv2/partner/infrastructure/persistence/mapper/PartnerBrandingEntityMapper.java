package uz.murodjon.robotcallv2.partner.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.infrastructure.persistence.entity.PartnerBrandingEntity;

@Component
public class PartnerBrandingEntityMapper {

    public PartnerBranding toPartnerBranding(PartnerBrandingEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PartnerBranding(entity.getCompanyId(), entity.getProductName(), entity.getDomain(),
                entity.getLogoUrl(), entity.getPrimaryColor(), entity.getSupportEmail(), entity.getSupportPhone(),
                entity.getUpdatedAt());
    }

    /** The editable fields onto {@code entity}; its id and timestamp are the adapter's. */
    public void copyInto(PartnerBranding branding, PartnerBrandingEntity entity) {
        entity.setProductName(branding.productName());
        entity.setDomain(branding.domain());
        entity.setLogoUrl(branding.logoUrl());
        entity.setPrimaryColor(branding.primaryColor());
        entity.setSupportEmail(branding.supportEmail());
        entity.setSupportPhone(branding.supportPhone());
    }
}
