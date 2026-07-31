package uz.murodjon.uysotvoice.campaign.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.agent.tts.TtsVoiceCatalog;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.campaign.dto.AddTargetRequest;
import uz.murodjon.uysotvoice.campaign.dto.AddTargetsResponse;
import uz.murodjon.uysotvoice.campaign.dto.CampaignFilter;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.dto.CampaignStatusResponse;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignRequest;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignResponse;
import uz.murodjon.uysotvoice.campaign.dto.ParsedTarget;
import uz.murodjon.uysotvoice.campaign.dto.TargetCsvParseResult;
import uz.murodjon.uysotvoice.campaign.dto.TargetFilter;
import uz.murodjon.uysotvoice.campaign.dto.TargetImportResult;
import uz.murodjon.uysotvoice.campaign.dto.TargetRow;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.dialer.config.DialerProperties;
import uz.murodjon.uysotvoice.dialer.config.RetryProperties;
import uz.murodjon.uysotvoice.dialer.service.RetrySchedule;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallResponse;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.csv.CsvRowError;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.PhoneNumbers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    private final DoNotCallRepository doNotCallList;
    private final TtsVoiceCatalog voices;
    private final DialerProperties dialerProps;
    private final AuditService audit;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, CampaignTargetRepository targets,
                           DoNotCallRepository doNotCallList, TtsVoiceCatalog voices,
                           DialerProperties dialerProps, AuditService audit, Clock clock
    ) {
        this.campaigns = campaigns;
        this.targets = targets;
        this.doNotCallList = doNotCallList;
        this.voices = voices;
        this.dialerProps = dialerProps;
        this.audit = audit;
        this.clock = clock;
    }

    /** Weekdays only, matching the V5 column default (§11.2). */
    private static final String DEFAULT_DIAL_DAYS = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";
//todo createCampaign bitta bolishi kerak, oshanda CreateCampaignRequest ni ozi kirib kelayversin
    public long createCampaign(String name, String type, String goalPrompt, String defaultLanguage,
                               LocalTime windowStart, LocalTime windowEnd, String dialDays,
                               int maxAttempts, int retryIntervalHours, int maxConcurrentCalls,
                               String ttsVoice, int dailyCallCap) {
        long id = campaigns.create(
                name,
                type != null ? type : "DEBT_COLLECTION",
                goalPrompt != null ? goalPrompt : "",
                "{}",
                defaultLanguage != null ? defaultLanguage : "uz-UZ",
                windowStart != null ? windowStart : LocalTime.of(9, 0),
                windowEnd != null ? windowEnd : LocalTime.of(20, 0),
                dialDays != null && !dialDays.isBlank() ? dialDays : DEFAULT_DIAL_DAYS,
                maxAttempts > 0 ? maxAttempts : 3,
                retryIntervalHours > 0 ? retryIntervalHours : 24,
                maxConcurrentCalls > 0 ? maxConcurrentCalls : 20,
                requireKnownVoice(ttsVoice),
                Math.max(0, dailyCallCap));
        audit.record("CAMPAIGN_CREATE", "campaign", String.valueOf(id),
                name + " (" + defaultLanguage + ", cap/day=" + Math.max(0, dailyCallCap) + ")");
        return id;
    }

    public CreateCampaignResponse createCampaign(CreateCampaignRequest r) {
        long id = createCampaign(r.name(), r.type(), r.goalPrompt(), r.defaultLanguage(),
                r.dialWindowStart(), r.dialWindowEnd(), r.dialDays(),
                r.maxAttempts(), r.retryIntervalHours(), r.maxConcurrentCalls(), r.ttsVoice(),
                r.dailyCallCap());
        return new CreateCampaignResponse(id, "DRAFT");
    }

    /**
     * Validate the chosen voice against the catalog, or {@code null} for "use the
     * configured routing".
     *
     * <p>Rejecting an unknown id here is the point of validating at all: the router
     * falls back to default routing for a voice it cannot resolve, so a typo would
     * otherwise surface as a whole campaign quietly dialled in the wrong voice.
     */
    private String requireKnownVoice(String ttsVoice) {
        if (ttsVoice == null || ttsVoice.isBlank()) {
            return null;
        }
        String trimmed = ttsVoice.trim();
        if (voices.find(trimmed) == null) {
            throw new ValidationException("Unknown TTS voice '" + trimmed + "'; available: " + voices.ids());
        }
        return trimmed;
    }

    public long addTarget(long campaignId, long clientId, String phone, String language, JsonNode contextData) {
        String json = contextData != null && !contextData.isNull() ? contextData.toString() : "{}";
        // Reject a bad number at import time rather than at dial time: a target that
        // can never be dialled would otherwise burn all its retries first.
        return targets.add(campaignId, clientId, PhoneNumbers.require(phone), language, json);
    }

    public AddTargetsResponse addTargets(long campaignId, List<AddTargetRequest> requests) {
        List<Long> ids = requests.stream()
                .map(t -> addTarget(campaignId, t.clientId(), t.phone(), t.language(), t.contextData()))
                .toList();
        return new AddTargetsResponse(campaignId, ids.size(), ids);
    }

    /**
     * Import targets from a CSV export (§10).
     *
     * <p>A row that cannot be used is reported and skipped rather than failing the file: a
     * few thousand rows from a CRM export will contain a handful of bad numbers, and
     * rejecting the whole import leaves the operator to find them by eye. The response
     * carries the line numbers.
     */
    public TargetImportResult importTargetsCsv(long campaignId, String csv) {
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<Long> added = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());
        for (ParsedTarget t : parsed.targets()) {
            try {
                added.add(targets.add(campaignId, t.clientId(), PhoneNumbers.require(t.phone()),
                        t.language(), t.contextJson()));
            } catch (Exception e) {
                // Almost always an unusable phone number — the one error worth naming per row.
                errors.add(new CsvRowError(t.line(), e.getMessage()));
            }
        }
        audit.record("TARGETS_IMPORT", "campaign", String.valueOf(campaignId),
                added.size() + " added, " + errors.size() + " rejected");
        log.info("CSV import into campaign {}: {} added, {} rejected, unknown columns {}",
                campaignId, added.size(), errors.size(), parsed.unknownColumns());
        return new TargetImportResult(campaignId, added.size(), added, errors, parsed.unknownColumns());
    }

    public PageableData<CampaignRow> listCampaigns(CampaignFilter filter) {
        List<CampaignRow> rows = campaigns.findAll(filter);
        long total = campaigns.count();
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** @return the campaign, or {@code null} if there is no such id. */
    public CampaignRow getCampaign(long id) {
        return campaigns.find(id);
    }

    /** As {@link #getCampaign(long)}, for the REST API — a missing campaign is a 404, not a null. */
    public CampaignRow requireCampaign(long id) {
        CampaignRow campaign = getCampaign(id);
        if (campaign == null) {
            throw new NotFoundException("campaign", id);
        }
        return campaign;
    }

    public PageableData<TargetRow> listTargets(long campaignId, TargetFilter filter) {
        List<TargetRow> rows = targets.findByCampaign(campaignId, filter);
        long total = targets.countByCampaign(campaignId);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    public void setStatus(long campaignId, String status) {
        campaigns.updateStatus(campaignId, status);
        audit.record("CAMPAIGN_" + status, "campaign", String.valueOf(campaignId), null);
    }

    public CampaignStatusResponse start(long campaignId) {
        setStatus(campaignId, "ACTIVE");
        return new CampaignStatusResponse(campaignId, "ACTIVE");
    }

    public CampaignStatusResponse pause(long campaignId) {
        setStatus(campaignId, "PAUSED");
        return new CampaignStatusResponse(campaignId, "PAUSED");
    }

    /**
     * Opt a target out. The phone-level list is what makes it stick across future
     * campaigns (§11.4); the per-target flag is kept so this campaign's own reporting
     * still shows why the target stopped. Transactional: both writes describe one
     * opt-out decision and must land together.
     */
    @Transactional
    public void doNotCall(long targetId) {
        TargetRow t = targets.find(targetId);
        if (t != null) {
            doNotCallList.add(t.phone(), "opted out via API", "MANUAL");
        }
        targets.setDoNotCall(targetId);
        audit.record("TARGET_DO_NOT_CALL", "target", String.valueOf(targetId),
                t != null ? t.phone() : null);
    }

    public DoNotCallResponse markDoNotCall(long targetId) {
        doNotCall(targetId);
        return new DoNotCallResponse(targetId, true);
    }

    /**
     * Apply a finished call's disposition to its target: mark it done, exhaust it, or
     * schedule the next attempt.
     *
     * <p>The retry time is chosen from the disposition and then pulled into the campaign's
     * dial window — see {@link RetrySchedule} for why both halves matter.
     */
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
        if (t.attempts() >= maxAttempts) {
            targets.updateStatus(targetId, "EXHAUSTED", null);
            log.info("Target {} EXHAUSTED after {} attempts", targetId, t.attempts());
            return;
        }
        Instant next = nextAttemptAt(disposition, c);
        targets.updateStatus(targetId, "PENDING", next);
        log.info("Target {} rescheduled ({}, attempt {}/{}) -> {}",
                targetId, disposition, t.attempts(), maxAttempts, next);
    }

    /** When to dial this target again, honouring the campaign's window (§11.2). */
    private Instant nextAttemptAt(Disposition disposition, CampaignRow campaign) {
        int retryHours = campaign != null ? campaign.retryIntervalHours() : 24;
        Duration delay = RetrySchedule.delayFor(disposition, dialerProps.retry(), retryHours);
        ZonedDateTime candidate = ZonedDateTime.now(clock).plus(delay);
        RetryProperties retry = dialerProps.retry();
        if (campaign == null || retry == null || !retry.respectDialWindow()) {
            return candidate.toInstant();
        }
        return RetrySchedule.intoWindow(candidate, campaign.allowedDays(),
                campaign.dialWindowStart(), campaign.dialWindowEnd()).toInstant();
    }

    private static boolean isTerminal(Disposition d) {
        return d == Disposition.PROMISE_TO_PAY
                || d == Disposition.REFUSED
                || d == Disposition.TRANSFERRED
                || d == Disposition.WRONG_NUMBER
                // An opt-out must never be retried, whatever the attempt count (§11.4).
                || d == Disposition.DO_NOT_CALL;
    }
}
