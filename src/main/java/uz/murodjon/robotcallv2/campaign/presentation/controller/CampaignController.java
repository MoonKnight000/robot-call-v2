package uz.murodjon.robotcallv2.campaign.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Campaign management API (PROJECT.md §5.2, §10).
 */
@RequestMapping("/api")
public interface CampaignController {

    @PostMapping("/campaigns")
    ResponseEntity<ResponseData<CreateCampaignResponse>> create(@Valid @RequestBody CreateCampaignRequest r);

    @PostMapping({"/campaigns/filter", "/campaigns/list"})
    ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(@Valid @RequestBody CampaignFilter filter);

    @GetMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignRow>> get(@PathVariable long id);

    @PutMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignRow>> update(@PathVariable long id, @Valid @RequestBody UpdateCampaignRequest r);

    @DeleteMapping("/campaigns/{id:\\d+}")
    ResponseEntity<ResponseData<CampaignStatusResponse>> archive(@PathVariable long id);

    @PostMapping("/campaigns/{id:\\d+}/clone")
    ResponseEntity<ResponseData<CampaignRow>> clone(@PathVariable long id);

    @PostMapping("/campaigns/{id:\\d+}/targets")
    ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(@PathVariable long id, @Valid @RequestBody List<AddTargetRequest> targets);

    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(@PathVariable long id, @RequestBody String csv);

    @PostMapping(value = "/campaigns/{id:\\d+}/targets/csv/preview", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(@PathVariable long id, @RequestBody String csv);

    @PostMapping("/campaigns/{id:\\d+}/targets/list")
    ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(@PathVariable long id, @Valid @RequestBody TargetFilter filter);

    @PostMapping("/campaigns/{id:\\d+}/start")
    ResponseEntity<ResponseData<CampaignStatusResponse>> start(@PathVariable long id);

    @PostMapping("/campaigns/{id:\\d+}/pause")
    ResponseEntity<ResponseData<CampaignStatusResponse>> pause(@PathVariable long id);

    @PostMapping("/targets/{id:\\d+}/do-not-call")
    ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(@PathVariable long id);

    @GetMapping("/campaigns/{campaignId:\\d+}/targets/{targetId:\\d+}/memory")
    ResponseEntity<ResponseData<TargetMemoryDto>> getTargetMemory(@PathVariable long campaignId, @PathVariable long targetId);

    @PutMapping("/campaigns/{campaignId:\\d+}/targets/{targetId:\\d+}/memory")
    ResponseEntity<ResponseData<TargetMemoryDto>> updateTargetMemory(@PathVariable long campaignId, @PathVariable long targetId, @Valid @RequestBody UpdateTargetMemoryRequest r);
}
