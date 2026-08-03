package uz.murodjon.uysotvoice.campaign.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
import uz.murodjon.uysotvoice.campaign.service.CampaignService;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallResponse;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

@RestController
public class CampaignControllerImpl implements CampaignController {

    private final CampaignService service;

    public CampaignControllerImpl(CampaignService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<CreateCampaignResponse>> create(CreateCampaignRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.createCampaign(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignRow>>> list(CampaignFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.listCampaigns(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.campaignRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> update(long id, UpdateCampaignRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.updateCampaign(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> archive(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.archive(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> clone(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.clone(id)));
    }

    @Override
    public ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(long id, List<AddTargetRequest> targets) {
        return ResponseEntity.ok(ResponseData.ok(service.addTargets(id, targets)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(long id, String csv) {
        return ResponseEntity.ok(ResponseData.ok(service.importTargetsCsv(id, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(long id, String csv) {
        return ResponseEntity.ok(ResponseData.ok(service.previewTargetsCsv(id, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(long id, TargetFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.listTargets(id, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> start(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.start(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> pause(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.pause(id)));
    }

    @Override
    public ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.markDoNotCall(id)));
    }
}
