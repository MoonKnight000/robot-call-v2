package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.campaign.application.dto.AddTargetRequest;
import uz.murodjon.robotcallv2.campaign.application.dto.AutoEnrollPreviewResponse;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignAutoEnrollUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.AutoEnrollContactRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollCandidate;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignKind;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds contacts to AUTO_ENROLL campaigns as they meet the campaign's rules. The scheduler
 * calls {@link #enrollMatchingContacts} for every ACTIVE campaign of that kind on each sweep, so
 * a contact created or edited to match is picked up within a sweep, and a campaign started
 * after the contacts exist catches up on them.
 */
@Service
public class AutoEnrollService implements CampaignAutoEnrollUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutoEnrollService.class);

    /** Contacts enrolled per campaign per sweep; a longer backlog is worked off over the next sweeps. */
    static final int SWEEP_BATCH = 500;
    private static final int MAX_PREVIEW_PAGE = 100;
    /** Retell's limit for one campaign's contacts. */
    private static final int MAX_SNAPSHOT = 100_000;

    private final AutoEnrollContactRepository autoEnrollContactRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignTargetService campaignTargetService;
    private final ObjectMapper objectMapper;

    public AutoEnrollService(AutoEnrollContactRepository autoEnrollContactRepository,
                             CampaignRepository campaignRepository, CampaignTargetService campaignTargetService,
                             ObjectMapper objectMapper) {
        this.autoEnrollContactRepository = autoEnrollContactRepository;
        this.campaignRepository = campaignRepository;
        this.campaignTargetService = campaignTargetService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AutoEnrollPreviewResponse previewAutoEnroll(long companyId, long campaignId, AutoEnrollment draft,
                                                       int page, int size) {
        Campaign campaign = campaignRepository.find(companyId, campaignId);
        if (campaign == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
        }
        AutoEnrollment source = draft != null ? draft : campaign.autoEnroll();
        // A rule still being filled in (no field, operator or value yet) is left out, not refused: the count is
        // what the finished rows match, as Retell's "Enrollment" shows it while the filter is edited. Unticked
        // contacts are listed too — the list is where they are ticked back.
        AutoEnrollment rules = new AutoEnrollment(source.matchAll(),
                source.rules().stream()
                        .filter(r -> r != null && r.field() != null && r.operator() != null
                                && r.value() != null && !r.value().isBlank())
                        .toList(),
                List.of());
        int pageSize = Math.min(MAX_PREVIEW_PAGE, Math.max(1, size));
        int offset = (Math.max(1, page) - 1) * pageSize;
        return new AutoEnrollPreviewResponse(
                autoEnrollContactRepository.countMatching(companyId, rules),
                autoEnrollContactRepository.findMatchingPage(companyId, rules, offset, pageSize));
    }

    /**
     * A STANDARD campaign's filter, taken at launch (Retell's snapshot): every contact matching it now, less the
     * unticked ones, becomes a target. Later matches do not join.
     *
     * @return how many contacts became targets
     */
    int snapshotFilteredContacts(Campaign campaign) {
        int total = 0;
        int added;
        do {
            added = enrollMatchingContacts(campaign);
            total += added;
        } while (added > 0 && total < MAX_SNAPSHOT);
        log.info("Campaign {} ({}) took {} filtered contact(s) at launch", campaign.id(), campaign.name(), total);
        return total;
    }

    /** How many contacts a STANDARD campaign's filter would take if it were launched now. */
    long countFilteredContacts(Campaign campaign) {
        return autoEnrollContactRepository.countMatching(campaign.companyId(), campaign.autoEnroll());
    }

    /** One sweep over every ACTIVE auto-enroll campaign; a failing campaign does not stop the rest. */
    void enrollAllActiveCampaigns() {
        for (Campaign campaign : campaignRepository.findActive()) {
            if (campaign.kind() != CampaignKind.AUTO_ENROLL || campaign.autoEnroll().hasNoRules()) {
                continue;
            }
            try {
                int added = enrollMatchingContacts(campaign);
                if (added > 0) {
                    log.info("Auto-enroll campaign {} ({}) enrolled {} contact(s)", campaign.id(), campaign.name(), added);
                }
            } catch (Exception e) {
                log.error("Auto-enroll of campaign {} failed: {}", campaign.id(), e.getMessage(), e);
            }
        }
    }

    /** @return how many contacts became targets */
    int enrollMatchingContacts(Campaign campaign) {
        List<AutoEnrollCandidate> candidates = autoEnrollContactRepository.findMatching(
                campaign.companyId(), campaign.autoEnroll(), campaign.id(), SWEEP_BATCH);
        if (candidates.isEmpty()) {
            return 0;
        }
        List<AddTargetRequest> requests = candidates.stream()
                .map(candidate -> new AddTargetRequest(0L, candidate.phone(), null, factsOf(candidate)))
                .toList();
        return campaignTargetService.addTargets(campaign.companyId(), campaign.id(), requests).added();
    }

    /**
     * The contact's own words about itself, as the target's facts. Notes stay out: they are the
     * company's, written for its staff, and a scenario's fact schema should not have to guard
     * against reading them aloud.
     */
    private JsonNode factsOf(AutoEnrollCandidate candidate) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("clientName", candidate.name());
        if (candidate.address() != null && !candidate.address().isBlank()) {
            facts.put("address", candidate.address());
        }
        if (candidate.tags() != null && !candidate.tags().isBlank()) {
            facts.put("tags", candidate.tags());
        }
        return objectMapper.valueToTree(facts);
    }
}
