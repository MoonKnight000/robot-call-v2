package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.SpreadsheetCsv;
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

    /** Confirms the legal consent for the contacts being called; required before a DRAFT is launched. */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/consent")
    ResponseEntity<ResponseData<CampaignRow>> acceptConsent(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** The Overview tab: the campaign, its caller ID pool and what, if anything, blocks a launch. */
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @GetMapping("/campaigns/{id:\\d+}/overview")
    ResponseEntity<ResponseData<CampaignOverviewResponse>> overview(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /**
     * Which contacts a campaign's rules match now — nothing is enrolled: the count and one page of them (an
     * AUTO_ENROLL campaign's trigger, a STANDARD campaign's "Filter contacts" list). With a body, the rules being
     * edited and not saved yet (unfinished rows are left out); without one, the saved rules.
     */
    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @PostMapping("/campaigns/{id:\\d+}/auto-enroll/preview")
    ResponseEntity<ResponseData<AutoEnrollPreviewResponse>> previewAutoEnroll(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestBody(required = false) AutoEnrollment draft);

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

    /** Empties a draft's uploaded list (Retell: replace the file, or switch to a filter). Returns how many were removed. */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @DeleteMapping("/campaigns/{id:\\d+}/targets")
    ResponseEntity<ResponseData<Integer>> clearTargets(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestBody @Size(max = MAX_CSV_CHARS) String csv);

    /**
     * Retell "Map the columns": the file (CSV text, or an .xlsx read by {@code targets/xlsx}) with the person's
     * header → field mapping; {@code ""} leaves a column out.
     */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets/import")
    ResponseEntity<ResponseData<TargetImportResult>> importTargets(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody TargetImportRequest request);

    /** What {@code targets/import} would load with this mapping; nothing is saved. */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets/import/preview")
    ResponseEntity<ResponseData<TargetCsvPreview>> previewTargets(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody TargetImportRequest request);

    /** One sheet of an .xlsx file as CSV, for the mapping step ({@code sheet} from 0); nothing is imported. */
    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResponseData<SpreadsheetCsv>> readTargetsXlsx(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") int sheet);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv/preview", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @RequestBody @Size(max = MAX_CSV_CHARS) String csv);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PostMapping("/campaigns/{id:\\d+}/targets/batch")
    ResponseEntity<ResponseData<AddTargetsResponse>> addBatchTargets(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody List<BatchCallTargetItemDto> targets);

    @PreAuthorize("hasAuthority('CAMPAIGN_READ')")
    @PostMapping("/campaigns/target-source/preview-tool")
    ResponseEntity<ResponseData<ToolTargetPreviewResponse>> previewToolTargets(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ToolTargetPreviewRequest request);

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
