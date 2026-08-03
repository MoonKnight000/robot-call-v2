package uz.murodjon.uysotvoice.campaign.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.campaign.dto.AddTargetRequest;
import uz.murodjon.uysotvoice.campaign.dto.AddTargetsResponse;
import uz.murodjon.uysotvoice.campaign.dto.CampaignFilter;
import uz.murodjon.uysotvoice.campaign.dto.Campaign;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.dto.CampaignStatusResponse;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignRequest;
import uz.murodjon.uysotvoice.campaign.dto.CreateCampaignResponse;
import uz.murodjon.uysotvoice.campaign.dto.CsvColumnMapping;
import uz.murodjon.uysotvoice.campaign.dto.ParsedTarget;
import uz.murodjon.uysotvoice.campaign.dto.TargetCsvParseResult;
import uz.murodjon.uysotvoice.campaign.dto.TargetCsvPreview;
import uz.murodjon.uysotvoice.campaign.dto.TargetFilter;
import uz.murodjon.uysotvoice.campaign.dto.TargetImportResult;
import uz.murodjon.uysotvoice.campaign.dto.CampaignTarget;
import uz.murodjon.uysotvoice.campaign.dto.UpdateCampaignRequest;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.TargetStatus;
import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.campaign.repository.CampaignTargetRepository;
import uz.murodjon.uysotvoice.company.dto.CompanyConfig;
import uz.murodjon.uysotvoice.company.service.CompanyConfigService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.dialer.config.DialerProperties;
import uz.murodjon.uysotvoice.dialer.config.RetryProperties;
import uz.murodjon.uysotvoice.dialer.service.RetrySchedule;
import uz.murodjon.uysotvoice.donotcall.dto.DoNotCallResponse;
import uz.murodjon.uysotvoice.donotcall.enums.DoNotCallSource;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.notification.service.NotificationService;
import uz.murodjon.uysotvoice.scenario.service.ScenarioService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.csv.CsvRowError;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.PhoneNumbers;
import uz.murodjon.uysotvoice.user.service.UserService;
import uz.murodjon.uysotvoice.voice.service.TtsVoiceService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final TtsVoiceService voices;
    private final ScenarioService scenarios;
    private final UserService users;
    private final CompanyConfigService companyConfig;
    private final CurrentCompany currentCompany;
    private final DialerProperties dialerProps;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, CampaignTargetRepository targets,
                           DoNotCallRepository doNotCallList, TtsVoiceService voices, ScenarioService scenarios,
                           UserService users, CompanyConfigService companyConfig, CurrentCompany currentCompany,
                           DialerProperties dialerProps, AuditService audit, NotificationService notifications,
                           Clock clock
    ) {
        this.campaigns = campaigns;
        this.targets = targets;
        this.doNotCallList = doNotCallList;
        this.voices = voices;
        this.scenarios = scenarios;
        this.users = users;
        this.companyConfig = companyConfig;
        this.currentCompany = currentCompany;
        this.dialerProps = dialerProps;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Weekdays only, matching the old column default (§11.2).
     */
    private static final Set<DayOfWeek> DEFAULT_DIAL_DAYS = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    public CreateCampaignResponse createCampaign(CreateCampaignRequest r) {
        // 404s if the scenario is unknown or belongs to another company (ROADMAP A.3/B.2).
        scenarios.requireScenario(r.scenarioId());
        // Validated against the company's supported-language list, or that list's own
        // default when none was requested (ROADMAP B.1).
        String language = companyConfig.resolveLanguage(currentCompany.id(), r.defaultLanguage());
        int dailyCallCap = Math.max(0, r.dailyCallCap());
        LocalTime dialWindowStart = r.dialWindowStart() != null ? r.dialWindowStart() : LocalTime.of(9, 0);
        LocalTime dialWindowEnd = r.dialWindowEnd() != null ? r.dialWindowEnd() : LocalTime.of(20, 0);
        requireWindowWithinCompany(currentCompany.id(), dialWindowStart, dialWindowEnd);
        Campaign row = new Campaign(
                0,
                r.name(),
                r.type(),
                CampaignStatus.DRAFT,
                r.goalPrompt() != null ? r.goalPrompt() : "",
                language,
                dialWindowStart,
                dialWindowEnd,
                r.dialDays() != null && !r.dialDays().isEmpty() ? r.dialDays() : DEFAULT_DIAL_DAYS,
                r.maxAttempts() > 0 ? r.maxAttempts() : 3,
                r.retryIntervalHours() > 0 ? r.retryIntervalHours() : 24,
                r.maxConcurrentCalls() > 0 ? r.maxConcurrentCalls() : 20,
                requireKnownVoice(r.ttsVoice()),
                dailyCallCap,
                r.scenarioId(),
                currentCompany.id(),
                r.disclosureEnabled() == null || r.disclosureEnabled(),
                null);
        long id = campaigns.create(row);
        audit.record("CAMPAIGN_CREATE", "campaign", String.valueOf(id),
                r.name() + " (" + language + ", cap/day=" + dailyCallCap + ")");
        return new CreateCampaignResponse(id, CampaignStatus.DRAFT);
    }

    /**
     * Full edit of a campaign's configuration ("Tahrirlash", §10.6).
     */
    public CampaignRow updateCampaign(long id, UpdateCampaignRequest r) {
        Campaign existing = requireCampaign(id);
        String language = companyConfig.resolveLanguage(existing.companyId(), r.defaultLanguage());
        requireWindowWithinCompany(existing.companyId(), r.dialWindowStart(), r.dialWindowEnd());
        Campaign row = new Campaign(
                id,
                r.name(),
                existing.type(),
                existing.status(),
                r.goalPrompt(),
                language,
                r.dialWindowStart(),
                r.dialWindowEnd(),
                r.dialDays(),
                r.maxAttempts(),
                r.retryIntervalHours(),
                r.maxConcurrentCalls(),
                requireKnownVoice(r.ttsVoice()),
                Math.max(0, r.dailyCallCap()),
                existing.scenarioId(),
                existing.companyId(),
                r.disclosureEnabled(),
                existing.createdBy());
        campaigns.update(id, row);
        audit.record("CAMPAIGN_UPDATE", "campaign", String.valueOf(id), r.name());
        return campaignRow(id);
    }

    /**
     * "Nusxalash" (backend-uchun-talablar.md §3) — copies {@code id}'s configuration
     * into a brand new {@code DRAFT} campaign via {@link CampaignRepository#create},
     * which already forces {@code DRAFT}/current-company/empty-{@code scriptConfig}
     * and never touches {@code campaign_target} — so targets are never copied.
     */
    public CampaignRow clone(long id) {
        Campaign source = requireCampaign(id);
        Campaign row = new Campaign(
                0,
                source.name() + " (nusxa)",
                source.type(),
                CampaignStatus.DRAFT,
                source.goalPrompt(),
                source.defaultLanguage(),
                source.dialWindowStart(),
                source.dialWindowEnd(),
                source.dialDays(),
                source.maxAttempts(),
                source.retryIntervalHours(),
                source.maxConcurrentCalls(),
                source.ttsVoice(),
                source.dailyCallCap(),
                source.scenarioId(),
                source.companyId(),
                source.disclosureEnabled(),
                null);
        long newId = campaigns.create(row);
        audit.record("CAMPAIGN_CLONE", "campaign", String.valueOf(newId), "from " + id);
        return campaignRow(newId);
    }

    /**
     * Reject a campaign window that reaches outside the company's own dial window
     * (ROADMAP B.3): {@code DialerService} enforces the company's window as a strict
     * ceiling on top of the campaign's, so a campaign configured past it would never
     * be told why it silently stops dialing at the company's cutoff instead of its
     * own. A {@code null} campaign window (or a company with no config row) imposes
     * no ceiling, matching the dialer's own null-is-unrestricted handling.
     */
    private void requireWindowWithinCompany(long companyId, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return;
        }
        CompanyConfig config = companyConfig.find(companyId);
        if (config == null || config.dialWindowStart() == null || config.dialWindowEnd() == null) {
            return;
        }
        if (start.isBefore(config.dialWindowStart()) || end.isAfter(config.dialWindowEnd())) {
            throw new ValidationException("Campaign dial window (" + start + "-" + end
                    + ") must fit inside the company's allowed dial window ("
                    + config.dialWindowStart() + "-" + config.dialWindowEnd() + ")");
        }
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

    /**
     * "Ustunni moslashtirib, keyin tasdiqlash" wizard step (§10.6) — parses the file and
     * reports the column mapping plus a sample of parsed rows, without inserting anything.
     * The operator then confirms via {@link #importTargetsCsv}, which re-parses the same
     * file for real.
     */
    public TargetCsvPreview previewTargetsCsv(long campaignId, String csv) {
        requireCampaign(campaignId); // 404s if unknown or another company's
        List<CsvColumnMapping> columns = TargetCsvImporter.mapColumns(csv);
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<ParsedTarget> sample = parsed.targets().stream().limit(10).toList();
        return new TargetCsvPreview(columns, sample, parsed.targets().size(), parsed.errors(), parsed.unknownColumns());
    }

    /**
     * API-facing list (backend-uchun-talablar.md §16) — enriches each row with
     * {@code scenarioName}/{@code createdByName} via a batched lookup rather than one
     * query per row.
     */
    public PageableData<CampaignRow> listCampaigns(CampaignFilter filter) {
        List<Campaign> rows = campaigns.findAll(filter);
        long total = campaigns.count(filter);
        return PageableData.of(toRows(rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** Batched {@code scenarioName}/{@code createdByName} enrichment for a page of campaigns. */
    private List<CampaignRow> toRows(List<Campaign> rows) {
        Map<Long, String> scenarioNames = scenarios.scenarioNamesByIds(
                rows.stream().map(Campaign::scenarioId).collect(Collectors.toSet()));
        Map<Long, String> creatorNames = users.namesByIds(
                rows.stream().map(Campaign::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> CampaignRow.of(c, scenarioNames.get(c.scenarioId()),
                        c.createdBy() != null ? creatorNames.get(c.createdBy()) : null))
                .toList();
    }

    /** As {@link #listCampaigns}, for a single campaign — used by {@code get}/{@code update}/{@code clone}. */
    public CampaignRow campaignRow(long id) {
        Campaign c = requireCampaign(id);
        String scenarioName = scenarios.scenarioNamesByIds(List.of(c.scenarioId())).get(c.scenarioId());
        String createdByName = c.createdBy() != null
                ? users.namesByIds(List.of(c.createdBy())).get(c.createdBy())
                : null;
        return CampaignRow.of(c, scenarioName, createdByName);
    }

    /**
     * @return the campaign, or {@code null} if there is no such id.
     */
    public Campaign getCampaign(long id) {
        return campaigns.find(id);
    }

    /**
     * As {@link #getCampaign(long)}, for internal callers — a missing campaign is a 404, not a null.
     */
    public Campaign requireCampaign(long id) {
        Campaign campaign = getCampaign(id);
        if (campaign == null) {
            throw new NotFoundException("campaign", id);
        }
        return campaign;
    }

    public PageableData<CampaignTarget> listTargets(long campaignId, TargetFilter filter) {
        List<CampaignTarget> rows = targets.findByCampaign(campaignId, filter);
        long total = targets.countByCampaign(campaignId);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    public void setStatus(long campaignId, CampaignStatus status) {
        campaigns.updateStatus(campaignId, status);
        audit.record("CAMPAIGN_" + status.name(), "campaign", String.valueOf(campaignId), null);
    }

    public CampaignStatusResponse start(long campaignId) {
        setStatus(campaignId, CampaignStatus.ACTIVE);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ACTIVE);
    }

    public CampaignStatusResponse pause(long campaignId) {
        setStatus(campaignId, CampaignStatus.PAUSED);
        return new CampaignStatusResponse(campaignId, CampaignStatus.PAUSED);
    }

    /**
     * "Arxivlash" (§10.6 kartochka {@code ⋯} menyusi) — {@code DELETE /api/campaigns/{id}}
     * maps here rather than to a real row deletion, matching the codebase's soft-delete
     * precedent ({@code DoNotCall.removedAt}): a campaign's targets/calls/transcripts must
     * stay in the reports, so the row itself is never dropped, only marked terminal.
     */
    public CampaignStatusResponse archive(long campaignId) {
        requireCampaign(campaignId);
        setStatus(campaignId, CampaignStatus.ARCHIVED);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ARCHIVED);
    }

    /**
     * Opt a target out. The phone-level list is what makes it stick across future
     * campaigns (§11.4); the per-target flag is kept so this campaign's own reporting
     * still shows why the target stopped. Transactional: both writes describe one
     * opt-out decision and must land together.
     */
    @Transactional
    public void doNotCall(long targetId) {
        CampaignTarget t = targets.find(targetId);
        if (t != null) {
            doNotCallList.add(t.phone(), "opted out via API", DoNotCallSource.MANUAL);
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
        CampaignTarget t = targets.find(targetId);
        if (t == null) {
            return;
        }
        if (isTerminal(disposition)) {
            targets.updateStatus(targetId, TargetStatus.DONE, null);
            log.info("Target {} DONE ({})", targetId, disposition);
            checkCompletion(t.campaignId());
            return;
        }
        Campaign c = campaigns.find(t.campaignId());
        int maxAttempts = c != null ? c.maxAttempts() : 3;
        if (t.attempts() >= maxAttempts) {
            targets.updateStatus(targetId, TargetStatus.EXHAUSTED, null);
            log.info("Target {} EXHAUSTED after {} attempts", targetId, t.attempts());
            checkCompletion(t.campaignId());
            return;
        }
        Instant next = nextAttemptAt(disposition, c);
        targets.updateStatus(targetId, TargetStatus.PENDING, next);
        log.info("Target {} rescheduled ({}, attempt {}/{}) -> {}",
                targetId, disposition, t.attempts(), maxAttempts, next);
    }

    /**
     * When to dial this target again, honouring the campaign's window (§11.2).
     */
    private Instant nextAttemptAt(Disposition disposition, Campaign campaign) {
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

    /**
     * Once a target lands on a terminal status (DONE/EXHAUSTED), check whether it was
     * the campaign's last one still in the dial loop — if so the campaign is done
     * (§10.6 "Tugadi" badge) and the topbar bell (§0.8 {@code CAMPAIGN_FINISHED}) is
     * raised. Only an ACTIVE campaign can finish this way; PAUSED/ARCHIVED/DRAFT are
     * left alone even if they happen to have zero active targets.
     */
    private void checkCompletion(long campaignId) {
        Campaign c = campaigns.find(campaignId);
        if (c == null || c.status() != CampaignStatus.ACTIVE || targets.countActive(campaignId) > 0) {
            return;
        }
        setStatus(campaignId, CampaignStatus.COMPLETED);
        notifications.notify(c.companyId(), NotificationType.CAMPAIGN_FINISHED,
                "Kampaniya tugadi", "\"" + c.name() + "\" kampaniyasi barcha nishonlarni yakunladi", null);
    }

    private static boolean isTerminal(Disposition d) {
        return d == Disposition.PROMISE_TO_PAY
                || d == Disposition.REFUSED
                || d == Disposition.TRANSFERRED
                || d == Disposition.WRONG_NUMBER
                // A non-debt scenario ending normally (survey completed, message
                // acknowledged...) — nothing to retry (ROADMAP A.3).
                || d == Disposition.COMPLETED
                // An opt-out must never be retried, whatever the attempt count (§11.4).
                || d == Disposition.DO_NOT_CALL;
    }
}
