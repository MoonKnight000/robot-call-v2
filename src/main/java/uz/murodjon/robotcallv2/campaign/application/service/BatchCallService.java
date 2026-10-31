package uz.murodjon.robotcallv2.campaign.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.BatchCallUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

/**
 * The one-request batch call: running create, audience import (CSV, Tool or direct Targets),
 * and start in order. One transaction.
 */
@Service
public class BatchCallService implements BatchCallUseCase {

    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;

    public BatchCallService(CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase) {
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
    }

    @Override
    @Transactional
    public BatchCallResponse startBatchCall(long companyId, BatchCallRequest request) {
        long campaignId = campaignUseCase.createCampaign(companyId, new CreateCampaignRequest(
                request.name(), request.aiAgentId(), null, request.schedule(),
                request.retry(), request.limits(), null, null, null, null, null, null)).id();

        TargetImportResult imported;
        if (request.csv() != null && !request.csv().isBlank()) {
            imported = campaignTargetUseCase.importTargetsCsv(companyId, campaignId, request.csv());
        } else if (request.toolSource() != null) {
            BatchCallToolSourceDto ts = request.toolSource();
            campaignTargetUseCase.updateTargetSource(companyId, campaignId, new UpdateTargetSourceRequest(
                    TargetSourceProvider.TOOL, null, null, null, null, null,
                    ts.itemsPath(), ts.phoneField(), ts.clientIdField(), ts.languageField(),
                    false, true, true, null, ts.toolName(), ts.toolArguments()));
            TargetSyncResult sync = campaignTargetUseCase.syncTargetsFromSource(companyId, campaignId);
            imported = new TargetImportResult(campaignId, sync.added(), List.of(), sync.skippedDuplicates(),
                    sync.skippedDoNotCall(), sync.errors(), List.of());
        } else if (request.targets() != null && !request.targets().isEmpty()) {
            AddTargetsResponse res = campaignTargetUseCase.addBatchTargets(companyId, campaignId, request.targets());
            imported = new TargetImportResult(campaignId, res.added(), res.targetIds(), res.skippedDuplicates(),
                    res.skippedDoNotCall(), List.of(), List.of());
        } else {
            throw new ValidationException(ErrorCode.CSV_EMPTY);
        }

        if (request.consentConfirmed()) {
            campaignUseCase.acceptConsent(companyId, campaignId);
        }
        CampaignStatusResponse started = campaignUseCase.start(companyId, campaignId, request.immediate());
        return new BatchCallResponse(campaignId, imported, started.status());
    }
}
