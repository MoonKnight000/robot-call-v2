package uz.murodjon.robotcallv2.dialer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.PromptSafeText;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.InstantCallUseCase;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to execute immediate, real-time trigger outbound calls (e.g. Lead from website/CRM) in &lt;10s.
 */
@Service
public class InstantCallTriggerService implements InstantCallUseCase {

    private static final Logger log = LoggerFactory.getLogger(InstantCallTriggerService.class);

    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final ObjectMapper objectMapper;

    public InstantCallTriggerService(CampaignUseCase campaignUseCase,
                                     CampaignTargetUseCase campaignTargetUseCase,
                                     ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
    }

    /**
     * Enqueues an instant high-priority call. The dialer's next tick (every few seconds)
     * claims it: a new target is due at once and comes first in the claim order.
     *
     * <p>It used to run a whole dispatch cycle here, on the request thread and alongside
     * the scheduled one — two ticks at once read the same concurrency counters and both
     * dialled up to the limit.
     */
    @Override
    public InstantCallResponse trigger(long companyId, InstantCallRequest request) {
        // Normalised like every other target, so the do-not-call match in claimDue (which
        // compares stored strings) sees the same form the list is kept in.
        String phone = PhoneNumbers.require(request.phone());

        Campaign campaign = resolveCampaign(companyId, request.campaignId());
        String contextJson = writeFacts(request);

        // null lets the dialer fall back to the agent's language when it dispatches this target.
        long clientId = request.clientId() != null ? request.clientId() : 0L;
        long targetId = campaignTargetUseCase.addQueuedTarget(companyId,
                CampaignTarget.queued(campaign.id(), clientId, phone.trim(), null, contextJson));
        if (targetId == 0) {
            // Already waiting in this campaign: the pending target is the instant call.
            CampaignTarget queued = campaignTargetUseCase.findQueuedTarget(companyId, campaign.id(), phone.trim());
            targetId = queued != null ? queued.id() : 0;
        }
        log.info("Instant call triggered for company={} phone={} targetId={} clientId={}", companyId,
                PhoneMasker.mask(phone), targetId, clientId);

        return InstantCallResponse.queued(targetId);
    }

    /**
     * An explicit campaign has to be ACTIVE: a paused, draft or finished one used to answer
     * "queued" for a call the dialer would never place.
     */
    private Campaign resolveCampaign(long companyId, Long campaignId) {
        if (campaignId != null) {
            Campaign campaign = campaignUseCase.findCampaign(companyId, campaignId);
            if (campaign == null) {
                throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
            }
            if (campaign.status() != CampaignStatus.ACTIVE) {
                throw new ConflictException(ErrorCode.CAMPAIGN_NOT_ACTIVE, campaignId, campaign.status());
            }
            return campaign;
        }
        List<Campaign> active = campaignUseCase.findActiveCampaigns();
        return active.stream()
                .filter(campaign -> campaign.companyId() == companyId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.INSTANT_CALL_CAMPAIGN_NOT_FOUND));
    }

    private String writeFacts(InstantCallRequest request) {
        Map<String, Object> facts =
                request.contextData() != null ? new HashMap<>(request.contextData()) : new HashMap<>();
        if (request.clientName() != null && !request.clientName().isBlank()) {
            facts.put("clientName", PromptSafeText.sanitize(request.clientName().trim(), 100));
        }
        try {
            return objectMapper.writeValueAsString(facts);
        } catch (Exception e) {
            log.warn("Could not serialize instant-call context, falling back to empty: {}", e.getMessage());
            return "{}";
        }
    }
}
