package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.campaign.presentation.dto.AbTestReportResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantCreateRequest;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantResponse;
import uz.murodjon.robotcallv2.campaign.presentation.dto.CampaignVariantUpdateRequest;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Campaign A/B Testing Variants REST API.
 */
@RequestMapping("/api/campaigns/{campaignId:\\d+}/variants")
public interface CampaignVariantController {

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<CampaignVariantResponse>> create(@PathVariable long campaignId,
                                                                 @Valid @RequestBody CampaignVariantCreateRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/{variantId:\\d+}")
    ResponseEntity<ResponseData<CampaignVariantResponse>> get(@PathVariable long campaignId,
                                                              @PathVariable long variantId);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/{variantId:\\d+}")
    ResponseEntity<ResponseData<CampaignVariantResponse>> update(@PathVariable long campaignId,
                                                                 @PathVariable long variantId,
                                                                 @Valid @RequestBody CampaignVariantUpdateRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/{variantId:\\d+}")
    ResponseEntity<ResponseData<Void>> delete(@PathVariable long campaignId,
                                              @PathVariable long variantId);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<CampaignVariantResponse>>> list(@PathVariable long campaignId);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/report")
    ResponseEntity<ResponseData<AbTestReportResponse>> getReport(@PathVariable long campaignId);
}
