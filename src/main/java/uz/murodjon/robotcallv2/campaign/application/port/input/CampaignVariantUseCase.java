package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.application.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantUpdateRequest;

import java.util.List;

public interface CampaignVariantUseCase {

    CampaignVariantResponse create(long companyId, long campaignId, CampaignVariantCreateRequest request);

    CampaignVariantResponse get(long companyId, long campaignId, long variantId);

    CampaignVariantResponse update(long companyId, long campaignId, long variantId,
                                   CampaignVariantUpdateRequest request);

    void delete(long companyId, long campaignId, long variantId);

    List<CampaignVariantResponse> list(long companyId, long campaignId);

    AbTestReportResponse getReport(long companyId, long campaignId);

    /**
     * The variant this call should run, or null when the campaign is not A/B testing.
     *
     *
     * @param assignmentKey the target's phone number — what the assignment stays stable
     *                      for across retries; null draws at random
     */
    CampaignVariant findForCall(long companyId, long campaignId, String assignmentKey);

    void recordCall(long variantId);

    void recordAnswer(long variantId);

    void recordConversion(long variantId);
}
