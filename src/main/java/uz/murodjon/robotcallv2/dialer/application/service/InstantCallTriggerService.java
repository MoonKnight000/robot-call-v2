package uz.murodjon.robotcallv2.dialer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.dialer.application.dto.InstantCallResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.InstantCallUseCase;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to execute immediate, real-time trigger outbound calls (e.g. Lead from website/CRM) in &lt;10s.
 */
@Service
public class InstantCallTriggerService implements InstantCallUseCase {

    private static final Logger log = LoggerFactory.getLogger(InstantCallTriggerService.class);

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final DialerService dialerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InstantCallTriggerService(CampaignRepository campaignRepository,
                                     CampaignTargetRepository campaignTargetRepository,
                                     DialerService dialerService) {
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.dialerService = dialerService;
    }

    /**
     * Enqueues an instant high-priority call and triggers the dialer worker immediately.
     */
    @Override
    public InstantCallResponse trigger(long companyId, InstantCallRequest request) {
        String phone = request.phone();
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, "phone must not be blank");
        }

        Campaign campaign = resolveCampaign(companyId, request.campaignId());
        String contextJson = writeFacts(request);

        // null lets the dialer fall back to the agent's language when it dispatches this target.
        long targetId = campaignTargetRepository.add(companyId,
                CampaignTarget.queued(campaign.id(), 0L, phone.trim(), null, contextJson));
        log.info("Instant call triggered for company={} phone={} targetId={}", companyId, phone, targetId);

        // Run dialer dispatch cycle immediately
        dialerService.dispatch();

        return InstantCallResponse.queued(targetId);
    }

    private Campaign resolveCampaign(long companyId, Long campaignId) {
        if (campaignId != null) {
            Campaign campaign = campaignRepository.find(companyId, campaignId);
            if (campaign == null) {
                throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
            }
            return campaign;
        }
        List<Campaign> active = campaignRepository.findActive();
        return active.stream()
                .filter(campaign -> campaign.companyId() == companyId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND,
                        "No active campaign found for instant trigger"));
    }

    private String writeFacts(InstantCallRequest request) {
        Map<String, Object> facts =
                request.contextData() != null ? new HashMap<>(request.contextData()) : new HashMap<>();
        if (request.clientName() != null && !request.clientName().isBlank()) {
            facts.put("clientName", request.clientName().trim());
        }
        try {
            return objectMapper.writeValueAsString(facts);
        } catch (Exception e) {
            log.warn("Could not serialize instant-call context, falling back to empty: {}", e.getMessage());
            return "{}";
        }
    }
}
