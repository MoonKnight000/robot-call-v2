package uz.murodjon.robotcallv2.campaign.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.BatchCallUseCase;
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

    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final BatchCallUseCase batchCallUseCase;

    public CampaignControllerImpl(CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                                  BatchCallUseCase batchCallUseCase) {
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.batchCallUseCase = batchCallUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<CreateCampaignResponse>> create(long companyId, CreateCampaignRequest request) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.createCampaign(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<BatchCallResponse>> batchCall(long companyId, BatchCallRequest request) {
        return ResponseEntity.ok(ResponseData.ok(batchCallUseCase.startBatchCall(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignRow>>> filter(long companyId, CampaignFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.filterCampaigns(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.requireCampaignRow(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> update(long companyId, long id, UpdateCampaignRequest request) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.updateCampaign(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> archive(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.archive(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignRow>> clone(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.clone(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetSourceRow>> targetSource(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.findTargetSource(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetSourceRow>> updateTargetSource(long companyId, long id, UpdateTargetSourceRequest request) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.updateTargetSource(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteTargetSource(long companyId, long id) {
        campaignTargetUseCase.deleteTargetSource(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<TargetSyncResult>> syncTargets(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.syncTargetsFromSource(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<List<Debtor>>> previewUysotDebtors(long companyId, int limit) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.previewUysotDebtors(companyId, limit)));
    }

    @Override
    public ResponseEntity<ResponseData<AddTargetsResponse>> addTargets(long companyId, long id, List<AddTargetRequest> targetRequests) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.addTargets(companyId, id, targetRequests)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetImportResult>> addTargetsCsv(long companyId, long id, String csv) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.importTargetsCsv(companyId, id, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<TargetCsvPreview>> previewTargetsCsv(long companyId, long id, String csv) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.previewTargetsCsv(companyId, id, csv)));
    }

    @Override
    public ResponseEntity<ResponseData<AddTargetsResponse>> addBatchTargets(long companyId, long id, List<BatchCallTargetItemDto> targets) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.addBatchTargets(companyId, id, targets)));
    }

    @Override
    public ResponseEntity<ResponseData<ToolTargetPreviewResponse>> previewToolTargets(long companyId, ToolTargetPreviewRequest request) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.previewToolTargets(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<CampaignTarget>>> targets(long companyId, long id, TargetFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.listTargets(companyId, id, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> start(long companyId, long id, boolean immediate) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.start(companyId, id, immediate)));
    }

    @Override
    public ResponseEntity<ResponseData<CampaignStatusResponse>> pause(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignUseCase.pause(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<DoNotCallResponse>> doNotCall(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(campaignTargetUseCase.markDoNotCall(companyId, id)));
    }
}
