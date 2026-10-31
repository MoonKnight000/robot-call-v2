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
                request.retry(), request.limits(), null, null)).id();

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
            imported = new TargetImportResult(sync.added(), 0, sync.doNotCall(), List.of());
        } else if (request.targets() != null && !request.targets().isEmpty()) {
            AddTargetsResponse res = campaignTargetUseCase.addBatchTargets(companyId, campaignId, request.targets());
            imported = new TargetImportResult(res.added(), 0, res.doNotCall(), List.of());
        } else {
            throw new ValidationException(ErrorCode.CSV_EMPTY);
        }

        CampaignStatusResponse started = campaignUseCase.start(companyId, campaignId, request.immediate());
        return new BatchCallResponse(campaignId, imported, started.status());
    }
}
