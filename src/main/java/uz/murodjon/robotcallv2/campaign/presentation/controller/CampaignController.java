package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Campaign management API (PROJECT.md §5.2, §10, docs/api/campaigns.md).
 */
@RequestMapping("/api")
public interface CampaignController {

    /**
     * A CSV body is a raw String, so the 5 MB multipart limit never applies to it; this
     * caps it at about the same size (a 400, not an import of whatever was sent).
     */
    int MAX_CSV_CHARS = 5_000_000;

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns")
    ResponseEntity<ResponseData<CreateCampaignResponse>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateCampaignRequest request);

    /**
     * Agent + CSV + start in one request — the same as {@code POST /campaigns}, then
     * {@code .../targets/csv}, then {@code .../start}. Nothing is saved when any step is refused.
     */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/batch-call")
    ResponseEntity<ResponseData<BatchCallResponse>> batchCall(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody BatchCallRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @PostMapping({"/campaigns/filter", "/campaigns/list"})
    ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CampaignFilter filter);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignRow>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignRow>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateCampaignRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignStatusResponse>> archive(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/clone")
    ResponseEntity<ResponseData<CampaignRow>> clone(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets")
    ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody List<AddTargetRequest> targets);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestBody @Size(max = MAX_CSV_CHARS) String csv);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv/preview", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestBody @Size(max = MAX_CSV_CHARS) String csv);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/campaigns/{id:\\d+}/target-source")
    ResponseEntity<ResponseData<TargetSourceRow>> targetSource(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/campaigns/{id:\\d+}/target-source")
    ResponseEntity<ResponseData<TargetSourceRow>> updateTargetSource(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateTargetSourceRequest request);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/campaigns/{id:\\d+}/target-source")
    ResponseEntity<ResponseData<Void>> deleteTargetSource(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets/sync")
    ResponseEntity<ResponseData<TargetSyncResult>> syncTargets(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /**
     * Who a {@code UYSOT_DEBTORS} campaign would call today: overdue contracts read live from
     * Uysot, with the overdue amount, next payment and manager (docs/api/campaigns.md).
     */
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/campaigns/uysot-debtors/preview")
    ResponseEntity<ResponseData<List<Debtor>>> previewUysotDebtors(
            @CurrentCompanyId long companyId,
            @RequestParam(defaultValue = "20") int limit);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @PostMapping("/campaigns/{id:\\d+}/targets/list")
    ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody TargetFilter filter);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/start")
    ResponseEntity<ResponseData<CampaignStatusResponse>> start(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestParam(defaultValue = "false") boolean immediate);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/pause")
    ResponseEntity<ResponseData<CampaignStatusResponse>> pause(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/targets/{id:\\d+}/do-not-call")
    ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
