package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.presentation.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantUpdateRequest;

import java.util.List;

public interface CampaignVariantUseCase {

    CampaignVariantResponse create(long campaignId, CampaignVariantCreateRequest request);

    CampaignVariantResponse get(long campaignId, long variantId);

    CampaignVariantResponse update(long campaignId, long variantId, CampaignVariantUpdateRequest request);

    void delete(long campaignId, long variantId);

    List<CampaignVariantResponse> list(long campaignId);

    AbTestReportResponse getReport(long campaignId);

    /**
     * The variant this call should run, or null when the campaign is not A/B testing.
     *
     * <p>Takes {@code companyId} rather than reading {@link
     * uz.murodjon.robotcallv2.company.application.service.CurrentCompany}: the dialer calls
     * this from a scheduled sweep that serves every tenant, where there is no current
     * company to read (CLAUDE.md §4).
     *
     * @param assignmentKey the target's phone number — what the assignment stays stable
     *                      for across retries; null draws at random
     */
    CampaignVariant findForCall(long companyId, long campaignId, String assignmentKey);

    void recordCall(long variantId);

    void recordAnswer(long variantId);

    void recordConversion(long variantId);
}
