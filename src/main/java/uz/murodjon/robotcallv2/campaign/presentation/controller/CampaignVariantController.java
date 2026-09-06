package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.campaign.application.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignVariantUpdateRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Campaign A/B Testing Variants REST API.
 */
@RequestMapping("/api/campaigns/{campaignId:\\d+}/variants")
public interface CampaignVariantController {

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CampaignVariantResponse>> create(@CurrentCompanyId long companyId, @PathVariable long campaignId,
                                                                 @Valid @RequestBody CampaignVariantCreateRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/{variantId:\\d+}")
    ResponseEntity<ResponseData<CampaignVariantResponse>> get(@CurrentCompanyId long companyId, @PathVariable long campaignId,
                                                              @PathVariable long variantId);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/{variantId:\\d+}")
    ResponseEntity<ResponseData<CampaignVariantResponse>> update(@CurrentCompanyId long companyId, @PathVariable long campaignId,
                                                                 @PathVariable long variantId,
                                                                 @Valid @RequestBody CampaignVariantUpdateRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/{variantId:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId, @PathVariable long campaignId,
                                              @PathVariable long variantId);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<CampaignVariantResponse>>> list(@CurrentCompanyId long companyId, @PathVariable long campaignId);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/report")
    ResponseEntity<ResponseData<AbTestReportResponse>> getReport(@CurrentCompanyId long companyId, @PathVariable long campaignId);
}
