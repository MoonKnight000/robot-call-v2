package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.domain.service.RetrySchedule;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.user.application.service.UserService;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CampaignService implements CampaignUseCase, CampaignTargetUseCase {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaigns;
    private final CampaignTargetRepository targets;
    private final DoNotCallRepository doNotCallList;
    private final AiAgentUseCase aiAgents;
    private final UserService users;
    private final CompanyConfigService companyConfig;
    private final DialerProperties dialerProperties;
    private final AuditService audit;
    private final NotificationService notifications;
    private final TtsWarmup ttsWarmup;
    private final TargetSourceRepository targetSources;
    private final TargetApiImporter targetApiImporter;
    private final SecretCipher secretCipher;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, CampaignTargetRepository targets,
                           DoNotCallRepository doNotCallList, AiAgentUseCase aiAgents,
                           UserService users, CompanyConfigService companyConfig,
                           DialerProperties dialerProperties, AuditService audit, NotificationService notifications,
                           TtsWarmup ttsWarmup, TargetSourceRepository targetSources,
                           TargetApiImporter targetApiImporter, SecretCipher secretCipher, Clock clock
    ) {
        this.campaigns = campaigns;
        this.targets = targets;
        this.doNotCallList = doNotCallList;
        this.aiAgents = aiAgents;
        this.users = users;
        this.companyConfig = companyConfig;
        this.dialerProperties = dialerProperties;
        this.audit = audit;
        this.notifications = notifications;
        this.ttsWarmup = ttsWarmup;
        this.targetSources = targetSources;
        this.targetApiImporter = targetApiImporter;
        this.secretCipher = secretCipher;
        this.clock = clock;
    }

    private static final Set<DayOfWeek> DEFAULT_DIAL_DAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    @Override
    public CreateCampaignResponse createCampaign(long companyId, CreateCampaignRequest r) {
        AiAgent agent = aiAgents.requireAgent(companyId, r.aiAgentId());
        int dailyCallCap = Math.max(0, r.dailyCallCap());
        LocalTime dialWindowStart = r.dialWindowStart() != null ? r.dialWindowStart() : LocalTime.of(9, 0);
        LocalTime dialWindowEnd = r.dialWindowEnd() != null ? r.dialWindowEnd() : LocalTime.of(20, 0);
        requireWindowWithinCompany(companyId, dialWindowStart, dialWindowEnd);
        Campaign row = new Campaign(
                0,
                r.name(),
                r.type(),
                CampaignStatus.DRAFT,
                dialWindowStart,
                dialWindowEnd,
                r.dialDays() != null && !r.dialDays().isEmpty() ? r.dialDays() : DEFAULT_DIAL_DAYS,
                r.maxAttempts() > 0 ? r.maxAttempts() : 3,
                Math.max(0, r.retryIntervalMinutes()),
                r.maxConcurrentCalls() > 0 ? r.maxConcurrentCalls() : 20,
                dailyCallCap,
                agent.id(),
                companyId,
                null,
                r.recurrenceTypeOrDefault(),
                r.recurringDayOfMonth(),
                r.cronExpression(),
                r.autoResetTargetsOrDefault(),
                null);
        long id = campaigns.create(companyId, row);
        audit.record(companyId, "CAMPAIGN_CREATE", "campaign", String.valueOf(id),
                r.name() + " (agent " + agent.name() + ", cap/day=" + dailyCallCap
                        + ", recurrence=" + r.recurrenceTypeOrDefault() + ")");
        return new CreateCampaignResponse(id, CampaignStatus.DRAFT);
    }

    @Override
    public CampaignRow updateCampaign(long companyId, long id, UpdateCampaignRequest r) {
        Campaign existing = requireCampaign(companyId, id);
        AiAgent agent = aiAgents.requireAgent(existing.companyId(), r.aiAgentId());
        requireWindowWithinCompany(existing.companyId(), r.dialWindowStart(), r.dialWindowEnd());
        Campaign row = new Campaign(
                id,
                r.name(),
                existing.type(),
                existing.status(),
                r.dialWindowStart(),
                r.dialWindowEnd(),
                r.dialDays(),
                r.maxAttempts(),
                Math.max(0, r.retryIntervalMinutes()),
                r.maxConcurrentCalls(),
                Math.max(0, r.dailyCallCap()),
                agent.id(),
                existing.companyId(),
                existing.createdBy(),
                r.recurrenceTypeOrDefault(),
                r.recurringDayOfMonth(),
                r.cronExpression(),
                r.autoResetTargetsOrDefault(),
                existing.lastRunAt());
        campaigns.update(companyId, id, row);
        audit.record(companyId, "CAMPAIGN_UPDATE", "campaign", String.valueOf(id), r.name());
        return campaignRow(companyId, id);
    }

    @Override
    public CampaignRow clone(long companyId, long id) {
        Campaign source = requireCampaign(companyId, id);
        Campaign row = new Campaign(
                0,
                source.name() + " (nusxa)",
                source.type(),
                CampaignStatus.DRAFT,
                source.dialWindowStart(),
                source.dialWindowEnd(),
                source.dialDays(),
                source.maxAttempts(),
                source.retryIntervalMinutes(),
                source.maxConcurrentCalls(),
                source.dailyCallCap(),
                source.aiAgentId(),
                source.companyId(),
                null,
                source.recurrenceType(),
                source.recurringDayOfMonth(),
                source.cronExpression(),
                source.autoResetTargets(),
                null);
        long newId = campaigns.create(companyId, row);
        audit.record(companyId, "CAMPAIGN_CLONE", "campaign", String.valueOf(newId), "from " + id);
        return campaignRow(companyId, newId);
    }

    private void requireWindowWithinCompany(long companyId, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return;
        }
        CompanyConfig config = companyConfig.find(companyId);
        if (config == null || config.dialWindowStart() == null || config.dialWindowEnd() == null) {
            return;
        }
        if (start.isBefore(config.dialWindowStart()) || end.isAfter(config.dialWindowEnd())) {
            throw new ValidationException(ErrorCode.CAMPAIGN_DIAL_WINDOW_OUT_OF_RANGE,
                    start, end, config.dialWindowStart(), config.dialWindowEnd());
        }
    }

    @Override
    public long addTarget(long companyId, long campaignId, long clientId, String phone, String language,
                          JsonNode contextData) {
        String json = contextData != null && !contextData.isNull() ? contextData.toString() : "{}";
        return targets.add(companyId,
                CampaignTarget.queued(campaignId, clientId, PhoneNumbers.require(phone), language, json));
    }

    @Override
    public AddTargetsResponse addTargets(long companyId, long campaignId, List<AddTargetRequest> requests) {
        List<Long> ids = requests.stream()
                .map(t -> addTarget(companyId, campaignId, t.clientId(), t.phone(), t.language(), t.contextData()))
                .toList();
        return new AddTargetsResponse(campaignId, ids.size(), ids);
    }

    @Override
    public TargetImportResult importTargetsCsv(long companyId, long campaignId, String csv) {
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<Long> added = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());
        for (ParsedTarget t : parsed.targets()) {
            try {
                added.add(targets.add(companyId, CampaignTarget.queued(campaignId, t.clientId(),
                        PhoneNumbers.require(t.phone()), t.language(), t.contextJson())));
            } catch (Exception e) {
                errors.add(new CsvRowError(t.line(), e.getMessage()));
            }
        }
        audit.record(companyId, "TARGETS_IMPORT", "campaign", String.valueOf(campaignId),
                added.size() + " added, " + errors.size() + " rejected");
        log.info("CSV import into campaign {}: {} added, {} rejected, unknown columns {}",
                campaignId, added.size(), errors.size(), parsed.unknownColumns());
        return new TargetImportResult(campaignId, added.size(), added, errors, parsed.unknownColumns());
    }

    @Override
    public TargetCsvPreview previewTargetsCsv(long companyId, long campaignId, String csv) {
        requireCampaign(companyId, campaignId);
        List<CsvColumnMapping> columns = TargetCsvImporter.mapColumns(csv);
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<ParsedTarget> sample = parsed.targets().stream().limit(10).toList();
        return new TargetCsvPreview(columns, sample, parsed.targets().size(), parsed.errors(), parsed.unknownColumns());
    }

    @Override
    public TargetSourceRow findTargetSource(long companyId, long campaignId) {
        requireCampaign(companyId, campaignId);
        TargetSource source = targetSources.findByCampaignId(campaignId);
        return source != null ? TargetSourceRow.of(source) : null;
    }

    @Override
    public TargetSourceRow updateTargetSource(long companyId, long campaignId, UpdateTargetSourceRequest r) {
        requireCampaign(companyId, campaignId);
        TargetSource existing = targetSources.findByCampaignId(campaignId);
        // Omitted on an edit means "keep the secret you already have" — the API never
        // hands it back out, so a client re-saving the form has nothing to send back.
        String authHeaderValue = r.authHeaderValue() != null && !r.authHeaderValue().isBlank()
                ? encryptSecret(r.authHeaderValue())
                : (existing != null ? existing.authHeaderValue() : null);
        TargetSource saved = targetSources.upsert(campaignId, new TargetSource(
                campaignId,
                r.url().trim(),
                r.method(),
                r.requestBody(),
                r.authHeaderName(),
                authHeaderValue,
                r.itemsPath(),
                r.phoneField(),
                r.clientIdField(),
                r.languageField(),
                r.replaceTargets() != null && r.replaceTargets(),
                r.syncOnRecurrence() == null || r.syncOnRecurrence(),
                r.enabled() == null || r.enabled(),
                existing != null ? existing.lastSyncAt() : null,
                existing != null ? existing.lastSyncAdded() : null,
                existing != null ? existing.lastSyncError() : null));
        audit.record(companyId, "TARGET_SOURCE_UPDATE", "campaign", String.valueOf(campaignId), saved.url());
        return TargetSourceRow.of(saved);
    }

    @Override
    public void deleteTargetSource(long companyId, long campaignId) {
        requireCampaign(companyId, campaignId);
        targetSources.delete(campaignId);
        audit.record(companyId, "TARGET_SOURCE_DELETE", "campaign", String.valueOf(campaignId), null);
    }

    @Override
    public TargetSyncResult syncTargetsFromSource(long companyId, long campaignId) {
        requireCampaign(companyId, campaignId);
        TargetSource source = targetSources.findByCampaignId(campaignId);
        if (source == null) {
            throw new NotFoundException(ErrorCode.TARGET_SOURCE_NOT_FOUND, campaignId);
        }
        return importFromSource(companyId, source);
    }

    /**
     * @throws uz.murodjon.robotcallv2.shared.exception.ExternalServiceException when the
     *         endpoint could not be read — deliberately not swallowed, so a manual sync
     *         says what went wrong and leaves the existing list untouched
     */
    private TargetSyncResult importFromSource(long companyId, TargetSource source) {
        long campaignId = source.campaignId();
        List<CsvRowError> errors = new ArrayList<>();
        List<ParsedTarget> fetched;
        try {
            fetched = targetApiImporter.fetchTargets(source, decryptSecret(source.authHeaderValue()), errors);
        } catch (RuntimeException e) {
            targetSources.recordSync(campaignId, Instant.now(clock), 0, e.getMessage());
            throw e;
        }
        // Only once the endpoint has answered: clearing first would empty the campaign on
        // every failed fetch, and a campaign with no targets dials nobody all day.
        int removed = source.replaceTargets() ? targets.deleteByCampaignId(companyId, campaignId) : 0;
        List<Long> added = new ArrayList<>();
        for (ParsedTarget t : fetched) {
            try {
                added.add(targets.add(companyId, CampaignTarget.queued(campaignId, t.clientId(),
                        PhoneNumbers.require(t.phone()), t.language(), t.contextJson())));
            } catch (Exception e) {
                errors.add(new CsvRowError(t.line(), e.getMessage()));
            }
        }
        targetSources.recordSync(campaignId, Instant.now(clock), added.size(), null);
        audit.record(companyId, "TARGETS_SYNC", "campaign", String.valueOf(campaignId),
                added.size() + " added, " + removed + " removed, " + errors.size() + " rejected");
        log.info("Target source sync for campaign {}: {} fetched, {} added, {} removed, {} rejected",
                campaignId, fetched.size(), added.size(), removed, errors.size());
        return new TargetSyncResult(campaignId, fetched.size(), added.size(), removed, errors);
    }

    private String encryptSecret(String plaintext) {
        if (!secretCipher.available()) {
            throw new ExternalServiceException(ErrorCode.ENCRYPTION_KEY_NOT_SET, "target-source");
        }
        return secretCipher.encrypt(plaintext);
    }

    private String decryptSecret(String encrypted) {
        if (encrypted == null || encrypted.isBlank() || !secretCipher.available()) {
            return null;
        }
        return secretCipher.decrypt(encrypted);
    }

    @Override
    public PageableData<CampaignRow> filterCampaigns(long companyId, CampaignFilter filter) {
        List<Campaign> rows = campaigns.findAll(companyId, filter);
        long total = campaigns.count(companyId, filter);
        return PageableData.of(toRows(companyId, rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public PageableData<CampaignRow> listCampaigns(long companyId, CampaignFilter filter) {
        return filterCampaigns(companyId, filter);
    }

    private List<CampaignRow> toRows(long companyId, List<Campaign> rows) {
        Set<Long> campaignIds = rows.stream().map(Campaign::id).collect(Collectors.toSet());
        Map<Long, CampaignTargetStats> statsMap = targets.statsByCampaignIds(companyId, campaignIds);
        Map<Long, String> agentNames = aiAgents.findNamesByIds(
                rows.stream().map(Campaign::aiAgentId).collect(Collectors.toSet()));
        Map<Long, String> creatorNames = users.namesByIds(companyId,
                rows.stream().map(Campaign::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> CampaignRow.of(c, agentNames.get(c.aiAgentId()),
                        c.createdBy() != null ? creatorNames.get(c.createdBy()) : null,
                        statsMap.getOrDefault(c.id(), CampaignTargetStats.ZERO)))
                .toList();
    }

    @Override
    public CampaignRow campaignRow(long companyId, long id) {
        Campaign c = requireCampaign(companyId, id);
        String agentName = aiAgents.findNamesByIds(List.of(c.aiAgentId())).get(c.aiAgentId());
        String createdByName = c.createdBy() != null
                ? users.namesByIds(companyId, List.of(c.createdBy())).get(c.createdBy())
                : null;
        CampaignTargetStats stats = targets.statsByCampaignId(companyId, id);
        return CampaignRow.of(c, agentName, createdByName, stats);
    }

    @Override
    public Campaign getCampaign(long companyId, long id) {
        return campaigns.find(companyId, id);
    }

    @Override
    public Campaign requireCampaign(long companyId, long id) {
        Campaign campaign = getCampaign(companyId, id);
        if (campaign == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
        }
        return campaign;
    }

    @Override
    public PageableData<CampaignTarget> listTargets(long companyId, long campaignId, TargetFilter filter) {
        List<CampaignTarget> rows = targets.findByCampaign(companyId, campaignId, filter);
        long total = targets.countByCampaign(companyId, campaignId);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public CampaignTarget requireTarget(long companyId, long campaignId, long targetId) {
        CampaignTarget target = targets.find(companyId, targetId);
        if (target == null || target.campaignId() != campaignId) {
            throw new NotFoundException(ErrorCode.TARGET_NOT_FOUND, targetId);
        }
        return target;
    }

    public void setStatus(long companyId, long campaignId, CampaignStatus status) {
        campaigns.updateStatus(companyId, campaignId, status);
        audit.record(companyId, "CAMPAIGN_" + status.name(), "campaign", String.valueOf(campaignId), null);
    }

    @Override
    public CampaignStatusResponse start(long companyId, long campaignId, boolean immediate) {
        setStatus(companyId, campaignId, CampaignStatus.ACTIVE);
        if (immediate) {
            // A target waiting on a retry interval — or on a pause that lasted past its
            // slot — stays unclaimable until its own time comes, so a resumed campaign can
            // look active and dial nothing for hours. Clearing the booked time makes the
            // waiting targets due at the next tick. The dial window still governs: this
            // says "as soon as calling is allowed", never "call outside the window".
            int released = targets.clearSchedule(campaignId);
            audit.record(companyId, "CAMPAIGN_START_IMMEDIATE", "campaign", String.valueOf(campaignId),
                    released + " targets released");
        }
        ttsWarmup.warmUpForCampaign(companyId, campaignId);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ACTIVE);
    }

    @Override
    public CampaignStatusResponse pause(long companyId, long campaignId) {
        setStatus(companyId, campaignId, CampaignStatus.PAUSED);
        return new CampaignStatusResponse(campaignId, CampaignStatus.PAUSED);
    }

    @Override
    public CampaignStatusResponse archive(long companyId, long campaignId) {
        requireCampaign(companyId, campaignId);
        setStatus(companyId, campaignId, CampaignStatus.ARCHIVED);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ARCHIVED);
    }

    @Override
    @Transactional
    public void doNotCall(long companyId, long targetId) {
        CampaignTarget t = targets.find(companyId, targetId);
        if (t != null) {
            doNotCallList.add(companyId, t.phone(), "opted out via API", DoNotCallSource.MANUAL);
        }
        targets.setDoNotCall(companyId, targetId);
        audit.record(companyId, "TARGET_DO_NOT_CALL", "target", String.valueOf(targetId),
                t != null ? t.phone() : null);
    }

    @Override
    public DoNotCallResponse markDoNotCall(long companyId, long targetId) {
        doNotCall(companyId, targetId);
        return new DoNotCallResponse(targetId, true);
    }

    @Override
    @Transactional
    public void triggerRecurrenceRun(long companyId, long campaignId, boolean resetTargets) {
        if (resetTargets) {
            targets.resetTargetsForRecurrence(campaignId);
            log.info("Reset targets for recurring campaign {}", campaignId);
        }
        syncFromSourceQuietly(companyId, campaignId);
        campaigns.recordRecurrenceRun(campaignId, Instant.now(clock), CampaignStatus.ACTIVE);
        ttsWarmup.warmUpForCampaign(companyId, campaignId);
        log.info("Triggered recurring run for campaign {}", campaignId);
    }

    /**
     * Pulls today's list before the run starts, when the campaign has a source set to it.
     *
     * <p>A failure here is logged and stamped on the source rather than thrown: this runs
     * on the recurrence sweep, which serves every tenant, and one company's API being down
     * must not stop the others' campaigns from starting. The campaign then runs over the
     * list it already had — {@code replaceTargets} never clears it on a failed fetch.
     */
    private void syncFromSourceQuietly(long companyId, long campaignId) {
        TargetSource source = targetSources.findByCampaignId(campaignId);
        if (source == null || !source.enabled() || !source.syncOnRecurrence()) {
            return;
        }
        try {
            importFromSource(companyId, source);
        } catch (Exception e) {
            log.error("Target source sync failed for campaign {} — running over the existing list: {}",
                    campaignId, e.getMessage());
        }
    }

    @Override
    public void applyOutcome(long companyId, long targetId, Disposition disposition) {
        CampaignTarget t = targets.find(companyId, targetId);
        if (t == null) {
            return;
        }
        if (isTerminal(disposition)) {
            targets.updateStatus(targetId, TargetStatus.DONE, null);
            log.info("Target {} DONE ({})", targetId, disposition);
            checkCompletion(companyId, t.campaignId());
            return;
        }
        Campaign c = campaigns.find(companyId, t.campaignId());
        int maxAttempts = c != null ? c.maxAttempts() : 3;
        if (t.attempts() >= maxAttempts) {
            targets.updateStatus(targetId, TargetStatus.EXHAUSTED, null);
            log.info("Target {} EXHAUSTED after {} attempts", targetId, t.attempts());
            checkCompletion(companyId, t.campaignId());
            return;
        }
        Instant next = nextAttemptAt(disposition, c);
        targets.updateStatus(targetId, TargetStatus.PENDING, next);
        log.info("Target {} rescheduled ({}, attempt {}/{}) -> {}",
                targetId, disposition, t.attempts(), maxAttempts, next);
    }

    @Override
    public void scheduleCallback(long companyId, long targetId, Instant callbackAt) {
        CampaignTarget t = targets.find(companyId, targetId);
        if (t == null || callbackAt == null) {
            return;
        }
        Campaign c = campaigns.find(companyId, t.campaignId());
        ZonedDateTime candidate = callbackAt.atZone(ZoneId.of("Asia/Tashkent"));
        RetryProperties retry = dialerProperties.retry();
        Instant finalInstant = candidate.toInstant();
        if (c != null && retry != null && retry.respectDialWindow()) {
            finalInstant = RetrySchedule.intoWindow(candidate, c.allowedDays(),
                    c.dialWindowStart(), c.dialWindowEnd()).toInstant();
        }
        targets.updateStatus(targetId, TargetStatus.PENDING, finalInstant);
        log.info("Target {} scheduled for smart callback at {}", targetId, finalInstant);
    }

    private Instant nextAttemptAt(Disposition disposition, Campaign campaign) {
        int retryMinutes = campaign != null ? campaign.retryIntervalMinutes() : 0;
        Duration delay = RetrySchedule.delayFor(disposition, dialerProperties.retry(), retryMinutes);
        ZonedDateTime candidate = ZonedDateTime.now(clock).plus(delay);
        RetryProperties retry = dialerProperties.retry();
        if (campaign == null || retry == null || !retry.respectDialWindow()) {
            return candidate.toInstant();
        }
        return RetrySchedule.intoWindow(candidate, campaign.allowedDays(),
                campaign.dialWindowStart(), campaign.dialWindowEnd()).toInstant();
    }

    private void checkCompletion(long companyId, long campaignId) {
        Campaign c = campaigns.find(companyId, campaignId);
        if (c == null || c.status() != CampaignStatus.ACTIVE || targets.countActive(campaignId) > 0) {
            return;
        }
        setStatus(c.companyId(), campaignId, CampaignStatus.COMPLETED);
        notifications.notify(c.companyId(), NotificationType.CAMPAIGN_FINISHED,
                "Kampaniya tugadi", "\"" + c.name() + "\" kampaniyasi barcha nishonlarni yakunladi", null);
    }

    private static boolean isTerminal(Disposition d) {
        return d == Disposition.PROMISE_TO_PAY
                || d == Disposition.REFUSED
                || d == Disposition.TRANSFERRED
                || d == Disposition.WRONG_NUMBER
                || d == Disposition.COMPLETED
                || d == Disposition.DO_NOT_CALL;
    }
}
