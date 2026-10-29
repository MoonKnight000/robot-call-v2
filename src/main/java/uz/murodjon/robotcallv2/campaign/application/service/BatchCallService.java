package uz.murodjon.robotcallv2.campaign.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.BatchCallResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CampaignStatusResponse;
import uz.murodjon.robotcallv2.campaign.application.dto.CreateCampaignRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.TargetImportResult;
import uz.murodjon.robotcallv2.campaign.application.port.input.BatchCallUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;

/**
 * The one-request batch call: nothing of its own beyond running create, CSV import and start
 * in order. One transaction, so a start that is refused (agent disabled, no valid row in the
 * file) leaves no half-made draft campaign behind.
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
                request.name(), request.type(), request.aiAgentId(), null, request.schedule(),
                null, null, null, null)).id();
        TargetImportResult imported = campaignTargetUseCase.importTargetsCsv(companyId, campaignId, request.csv());
        CampaignStatusResponse started = campaignUseCase.start(companyId, campaignId, request.immediate());
        return new BatchCallResponse(campaignId, imported, started.status());
    }
}
