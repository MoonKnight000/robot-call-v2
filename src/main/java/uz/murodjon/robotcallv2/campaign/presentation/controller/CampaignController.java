package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Campaign management API (PROJECT.md §5.2, §10).
 */
@RequestMapping("/api")
public interface CampaignController {

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns")
    ResponseEntity<ResponseData<CreateCampaignResponse>> create(@CurrentCompanyId long companyId,
            @Valid @RequestBody CreateCampaignRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @PostMapping({"/campaigns/filter", "/campaigns/list"})
    ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(@CurrentCompanyId long companyId,
            @Valid @RequestBody CampaignFilter filter);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignRow>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignRow>> update(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody UpdateCampaignRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignStatusResponse>> archive(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/clone")
    ResponseEntity<ResponseData<CampaignRow>> clone(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets")
    ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody List<AddTargetRequest> targets);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(@CurrentCompanyId long companyId, @PathVariable long id,
            @RequestBody String csv);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv/preview", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(@CurrentCompanyId long companyId, @PathVariable long id,
            @RequestBody String csv);

    /** The campaign's API target source, or {@code null} when it loads its list by hand. */
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/campaigns/{id:\\d+}/target-source")
    ResponseEntity<ResponseData<TargetSourceRow>> targetSource(@CurrentCompanyId long companyId, @PathVariable long id);

    /**
     * Points the campaign at an endpoint of the company's own that answers with the list
     * to call. A recurring campaign fetches it again on every run, so "call today's
     * overdue clients every morning" needs no upload.
     */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/campaigns/{id:\\d+}/target-source")
    ResponseEntity<ResponseData<TargetSourceRow>> updateTargetSource(
            @CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody UpdateTargetSourceRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/campaigns/{id:\\d+}/target-source")
    ResponseEntity<ResponseData<Void>> deleteTargetSource(@CurrentCompanyId long companyId, @PathVariable long id);

    /** Fetches the list now rather than waiting for the next recurrence run. */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets/sync")
    ResponseEntity<ResponseData<TargetSyncResult>> syncTargets(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @PostMapping("/campaigns/{id:\\d+}/targets/list")
    ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(@CurrentCompanyId long companyId, @PathVariable long id,
            @Valid @RequestBody TargetFilter filter);

    /**
     * Starts (or resumes) a campaign.
     *
     * @param id        campaign to activate
     * @param immediate {@code true} to dial the waiting targets as soon as the campaign is
     *                  active, clearing the retry schedule they are holding; {@code false}
     *                  (the default) leaves every target on the time it is already booked
     *                  for. The dial window and the allowed days apply either way.
     */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/start")
    ResponseEntity<ResponseData<CampaignStatusResponse>> start(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean immediate);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/pause")
    ResponseEntity<ResponseData<CampaignStatusResponse>> pause(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/targets/{id:\\d+}/do-not-call")
    ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(@CurrentCompanyId long companyId, @PathVariable long id);
}
