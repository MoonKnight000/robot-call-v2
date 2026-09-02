package uz.murodjon.robotcallv2.dialer.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
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
public class InstantCallTriggerService {

    private static final Logger log = LoggerFactory.getLogger(InstantCallTriggerService.class);

    private final CampaignRepository campaigns;
    private final CampaignTargetRepository targets;
    private final DialerService dialer;
    private final ObjectMapper mapper = new ObjectMapper();

    public InstantCallTriggerService(CampaignRepository campaigns,
                                     CampaignTargetRepository targets,
                                     DialerService dialer) {
        this.campaigns = campaigns;
        this.targets = targets;
        this.dialer = dialer;
    }

    /**
     * Enqueues an instant high-priority call and triggers the dialer worker immediately.
     */
    public long triggerCall(long companyId, Long campaignId, String phone, String clientName, Map<String, Object> contextData) {
        if (phone == null || phone.isBlank()) {
            throw new ValidationException(ErrorCode.PHONE_INVALID, "phone must not be blank");
        }

        Campaign campaign;
        if (campaignId != null) {
            campaign = campaigns.find(campaignId);
            if (campaign == null || campaign.companyId() != companyId) {
                throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
            }
        } else {
            List<Campaign> activeList = campaigns.findActive();
            campaign = activeList.stream()
                    .filter(c -> c.companyId() == companyId)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, "No active campaign found for instant trigger"));
        }

        Map<String, Object> facts = contextData != null ? new HashMap<>(contextData) : new HashMap<>();
        if (clientName != null && !clientName.isBlank()) {
            facts.put("clientName", clientName.trim());
        }

        String contextJson;
        try {
            contextJson = mapper.writeValueAsString(facts);
        } catch (Exception e) {
            contextJson = "{}";
        }

        String lang = campaign.defaultLanguage() != null ? campaign.defaultLanguage() : "uz-UZ";
        long targetId = targets.add(campaign.id(), 0L, phone.trim(), lang, contextJson);
        log.info("Instant call triggered for company={} phone={} targetId={}", companyId, phone, targetId);

        // Run dialer dispatch cycle immediately
        dialer.dispatch();

        return targetId;
    }
}
