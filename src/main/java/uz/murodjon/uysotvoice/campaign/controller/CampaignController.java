package uz.murodjon.uysotvoice.campaign.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.campaign.dto.AddTargetRequest;
import uz.murodjon.uysotvoice.campaign.dto.AddTargetsResponse;
import uz.murodjon.uysotvoice.campaign.dto.CampaignFilter;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.dto.CampaignStatusResponse;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignRequest;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignResponse;
import uz.murodjon.uysotvoice.campaign.dto.TargetCsvPreview;
import uz.murodjon.uysotvoice.campaign.dto.TargetFilter;
import uz.murodjon.uysotvoice.campaign.dto.TargetImportResult;
import uz.murodjon.uysotvoice.campaign.dto.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.dto.UpdateCampaignRequest;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallResponse;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

/**
 * Campaign management API (PROJECT.md §5.2, §10). Create a campaign, load its
 * targets, then start it — the dialer picks it up on its next tick.
 */
@RequestMapping("/api")
public interface CampaignController {

    @PostMapping("/campaigns")
    ResponseEntity<ResponseData<CreateCampaignResponse>> create(@Valid @RequestBody CreateCampaignRequest r);

    @PostMapping("/campaigns/list")
    ResponseEntity<ResponseData<PageableData<CampaignRow>>> list(@Valid @RequestBody CampaignFilter filter);

    @GetMapping("/campaigns/{id}")
    ResponseEntity<ResponseData<CampaignRow>> get(@PathVariable long id);

    /** "Tahrirlash" (§10.6) — full edit of a campaign's configuration. */
    @PutMapping("/campaigns/{id}")
    ResponseEntity<ResponseData<CampaignRow>> update(@PathVariable long id, @Valid @RequestBody UpdateCampaignRequest r);

    /**
     * "Arxivlash" (§10.6 kartochka {@code ⋯} menyusi) — soft-archives the campaign
     * (status becomes {@code ARCHIVED}); its targets/calls/transcripts are kept.
     */
    @DeleteMapping("/campaigns/{id}")
    ResponseEntity<ResponseData<CampaignStatusResponse>> archive(@PathVariable long id);

    /**
     * "Nusxalash" (backend-uchun-talablar.md §3) — copies {@code id}'s configuration
     * (scenario, dial window, language, etc.) into a brand new {@code DRAFT} campaign;
     * targets are never copied. Body-less, mirroring {@link #start}/{@link #pause}.
     */
    @PostMapping("/campaigns/{id}/clone")
    ResponseEntity<ResponseData<CampaignRow>> clone(@PathVariable long id);

    @PostMapping("/campaigns/{id}/targets")
    ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(@PathVariable long id, @Valid @RequestBody List<AddTargetRequest> targets);

    /**
     * Bulk-load targets from a CSV export (§10). Send the file body as {@code text/csv}:
     *
     * <pre>
     * clientId,phone,language,clientName,debtAmount,currency,dueDate,contractNumber
     * 1001,998901234567,uz-UZ,Aziz Karimov,1500000,so'm,2026-07-01,UY-2026-00123
     * </pre>
     *
     * <p>Columns are matched by header name, so the order does not matter and extra columns
     * are reported as ignored. Bad rows are rejected individually — the response lists their
     * line numbers, and everything else is loaded.
     */
    @PostMapping(value = "/campaigns/{id}/targets/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(@PathVariable long id, @RequestBody String csv);

    /**
     * "Ustunni moslashtirib, keyin tasdiqlash" oldindan ko'rish (§10.6) — bir xil faylni
     * qabul qiladi, lekin hech narsani saqlamaydi: ustun moslashtirish jadvali, dastlabki
     * qatorlar namunasi va xatolarni qaytaradi. Operator tasdiqlagach xuddi shu fayl
     * {@link #addTargetsCsv} ga yuboriladi.
     */
    @PostMapping(value = "/campaigns/{id}/targets/csv/preview", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(@PathVariable long id, @RequestBody String csv);

    @PostMapping("/campaigns/{id}/targets/list")
    ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(@PathVariable long id, @Valid @RequestBody TargetFilter filter);

    @PostMapping("/campaigns/{id}/start")
    ResponseEntity<ResponseData<CampaignStatusResponse>> start(@PathVariable long id);

    @PostMapping("/campaigns/{id}/pause")
    ResponseEntity<ResponseData<CampaignStatusResponse>> pause(@PathVariable long id);

    @PostMapping("/targets/{id}/do-not-call")
    ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(@PathVariable long id);
}
