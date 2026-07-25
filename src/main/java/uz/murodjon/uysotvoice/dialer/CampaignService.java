package uz.murodjon.uysotvoice.dialer;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Campaign/target lifecycle and outcome handling (PROJECT.md §5.2, §10). Owns the
 * retry policy: terminal dispositions mark a target DONE; retryable ones reschedule
 * until {@code max_attempts}, then EXHAUSTED.
 */
@Service
public class CampaignService {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaigns;
    private final CampaignTargetRepository targets;

    public CampaignService(CampaignRepository campaigns, CampaignTargetRepository targets) {
        this.campaigns = campaigns;
        this.targets = targets;
    }

    public long createCampaign(String name, String type, String goalPrompt, String defaultLanguage,
                               LocalTime windowStart, LocalTime windowEnd,
                               int maxAttempts, int retryIntervalHours, int maxConcurrentCalls) {
        return campaigns.create(
                name,
                type != null ? type : "DEBT_COLLECTION",
                goalPrompt != null ? goalPrompt : "",
                "{}",
                defaultLanguage != null ? defaultLanguage : "uz-UZ",
                windowStart != null ? windowStart : LocalTime.of(9, 0),
                windowEnd != null ? windowEnd : LocalTime.of(20, 0),
                maxAttempts > 0 ? maxAttempts : 3,
                retryIntervalHours > 0 ? retryIntervalHours : 24,
                maxConcurrentCalls > 0 ? maxConcurrentCalls : 20);
    }

    public long addTarget(long campaignId, long clientId, String phone, String language, JsonNode contextData) {
        String json = contextData != null && !contextData.isNull() ? contextData.toString() : "{}";
        return targets.add(campaignId, clientId, phone, language, json);
    }

    public List<CampaignRow> listCampaigns() {
        return campaigns.findAll();
    }

    public CampaignRow getCampaign(long id) {
        return campaigns.find(id);
    }

    public List<TargetRow> listTargets(long campaignId) {
        return targets.findByCampaign(campaignId);
    }

    public void setStatus(long campaignId, String status) {
        campaigns.updateStatus(campaignId, status);
    }

    public void doNotCall(long targetId) {
        targets.setDoNotCall(targetId);
    }

    /** Apply a finished call's disposition to its target (status + retry). */
    public void applyOutcome(long targetId, Disposition disposition) {
        TargetRow t = targets.find(targetId);
        if (t == null) {
            return;
        }
        if (isTerminal(disposition)) {
            targets.updateStatus(targetId, "DONE", null);
            log.info("Target {} DONE ({})", targetId, disposition);
            return;
        }
        CampaignRow c = campaigns.find(t.campaignId());
        int maxAttempts = c != null ? c.maxAttempts() : 3;
        int retryHours = c != null ? c.retryIntervalHours() : 24;
        if (t.attempts() >= maxAttempts) {
            targets.updateStatus(targetId, "EXHAUSTED", null);
            log.info("Target {} EXHAUSTED after {} attempts", targetId, t.attempts());
        } else {
            Instant next = Instant.now().plus(retryHours, ChronoUnit.HOURS);
            targets.updateStatus(targetId, "PENDING", next);
            log.info("Target {} rescheduled ({}) -> {}", targetId, disposition, next);
        }
    }

    private static boolean isTerminal(Disposition d) {
        return d == Disposition.PROMISE_TO_PAY
                || d == Disposition.REFUSED
                || d == Disposition.TRANSFERRED
                || d == Disposition.WRONG_NUMBER;
    }
}
