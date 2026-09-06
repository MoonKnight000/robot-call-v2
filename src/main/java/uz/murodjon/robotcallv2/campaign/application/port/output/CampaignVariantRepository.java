package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;

import java.util.List;
import java.util.Optional;

public interface CampaignVariantRepository {

    CampaignVariant save(CampaignVariant variant);

    Optional<CampaignVariant> findByIdAndCompanyId(long id, long companyId);

    List<CampaignVariant> findAllByCampaignIdAndCompanyId(long campaignId, long companyId);

    void recordCall(long variantId);

    void recordAnswer(long variantId);

    void recordConversion(long variantId);

    void deleteByIdAndCompanyId(long id, long companyId);
}
