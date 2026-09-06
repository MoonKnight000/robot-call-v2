package uz.murodjon.robotcallv2.campaign.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class CampaignControllerImpl implements CampaignController {

    private final CampaignUseCase campaigns;
    private final CampaignTargetUseCase targets;

    public CampaignControllerImpl(CampaignUseCase campaigns, CampaignTargetUseCase targets) {
        this.campaigns = campaigns;
        this.targets = targets;
    }

    @Override
    public ResponseEntity<ResponseData<CreateCampaignResponse>> create(CreateCampaignRequest r) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.createCampaign(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(CampaignFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.filterCampaigns(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> get(long id) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.campaignRow(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> update(long id, UpdateCampaignRequest r) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.updateCampaign(id, r)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> archive(long id) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.archive(id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> clone(long id) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.clone(id)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetSourceRow>> targetSource(long id) {
        return ResponseEntity.ok(ResponseData.ok(targets.findTargetSource(id)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetSourceRow>> updateTargetSource(long id, UpdateTargetSourceRequest request) {
        return ResponseEntity.ok(ResponseData.ok(targets.updateTargetSource(id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteTargetSource(long id) {
        targets.deleteTargetSource(id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<TargetSyncResult>> syncTargets(long id) {
        return ResponseEntity.ok(ResponseData.ok(targets.syncTargetsFromSource(id)));
    }

    @Override
    public ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(long id, List<AddTargetRequest> targetRequests) {
        return ResponseEntity.ok(ResponseData.ok(targets.addTargets(id, targetRequests)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(long id, String csv) {
        return ResponseEntity.ok(ResponseData.ok(targets.importTargetsCsv(id, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(long id, String csv) {
        return ResponseEntity.ok(ResponseData.ok(targets.previewTargetsCsv(id, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(long id, TargetFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(targets.listTargets(id, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> start(long id, boolean immediate) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.start(id, immediate)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> pause(long id) {
        return ResponseEntity.ok(ResponseData.ok(campaigns.pause(id)));
    }

    @Override
    public ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(long id) {
        return ResponseEntity.ok(ResponseData.ok(targets.markDoNotCall(id)));
    }
}
