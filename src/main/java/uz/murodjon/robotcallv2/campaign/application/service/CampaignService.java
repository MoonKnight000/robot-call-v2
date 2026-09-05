package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.domain.service.RetrySchedule;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.user.application.service.UserService;
import uz.murodjon.robotcallv2.voice.application.service.TtsVoiceService;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CampaignService implements CampaignUseCase, CampaignTargetUseCase {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    private final TtsWarmup ttsWarmup;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, CampaignTargetRepository targets,
                           DoNotCallRepository doNotCallList, TtsVoiceService voices, ScenarioService scenarios,
                           UserService users, CompanyConfigService companyConfig, CurrentCompany currentCompany,
                           DialerProperties dialerProps, AuditService audit, NotificationService notifications,
                           TtsWarmup ttsWarmup, Clock clock
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
        this.ttsWarmup = ttsWarmup;
        this.clock = clock;
    }

    private static final Set<DayOfWeek> DEFAULT_DIAL_DAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    @Override
    public CreateCampaignResponse createCampaign(CreateCampaignRequest r) {
        scenarios.requireScenario(r.scenarioId());
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
                Math.max(0, r.retryIntervalMinutes()),
                r.maxConcurrentCalls() > 0 ? r.maxConcurrentCalls() : 20,
                requireKnownVoice(r.ttsVoice()),
                dailyCallCap,
                r.scenarioId(),
                currentCompany.id(),
                null,
                r.recurrenceTypeOrDefault(),
                r.recurringDayOfMonth(),
                r.cronExpression(),
                r.autoResetTargetsOrDefault(),
                null,
                r.ambientSoundOrDefault(),
                r.midCallSmsEnabledOrDefault(),
                r.midCallSmsTemplate(),
                r.voicemailActionOrDefault(),
                r.voicemailMessage(),
                r.dtmfInputEnabledOrDefault(),
                r.emotionAdaptiveVoiceOrDefault(),
                r.agentPersonaOrDefault(),
                requireKnownVoicePerLanguage(currentCompany.id(), r.languageVoicesOrEmpty()),
                r.sipTrunkIdsOrEmpty());
        long id = campaigns.create(row);
        audit.record("CAMPAIGN_CREATE", "campaign", String.valueOf(id),
                r.name() + " (" + language + ", cap/day=" + dailyCallCap + ", recurrence=" + r.recurrenceTypeOrDefault() + ")");
        return new CreateCampaignResponse(id, CampaignStatus.DRAFT);
    }

    @Override
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
                Math.max(0, r.retryIntervalMinutes()),
                r.maxConcurrentCalls(),
                requireKnownVoice(r.ttsVoice()),
                Math.max(0, r.dailyCallCap()),
                existing.scenarioId(),
                existing.companyId(),
                existing.createdBy(),
                r.recurrenceTypeOrDefault(),
                r.recurringDayOfMonth(),
                r.cronExpression(),
                r.autoResetTargetsOrDefault(),
                existing.lastRunAt(),
                r.ambientSoundOrDefault(),
                r.midCallSmsEnabledOrDefault(),
                r.midCallSmsTemplate(),
                r.voicemailActionOrDefault(),
                r.voicemailMessage(),
                r.dtmfInputEnabledOrDefault(),
                r.emotionAdaptiveVoiceOrDefault(),
                r.agentPersonaOrDefault(),
                requireKnownVoicePerLanguage(existing.companyId(), r.languageVoicesOrEmpty()),
                r.sipTrunkIdsOrEmpty());
        campaigns.update(id, row);
        audit.record("CAMPAIGN_UPDATE", "campaign", String.valueOf(id), r.name());
        return campaignRow(id);
    }

    @Override
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
                source.retryIntervalMinutes(),
                source.maxConcurrentCalls(),
                source.ttsVoice(),
                source.dailyCallCap(),
                source.scenarioId(),
                source.companyId(),
                null,
                source.recurrenceType(),
                source.recurringDayOfMonth(),
                source.cronExpression(),
                source.autoResetTargets(),
                null,
                source.ambientSound(),
                source.midCallSmsEnabled(),
                source.midCallSmsTemplate(),
                source.voicemailAction(),
                source.voicemailMessage(),
                source.dtmfInputEnabled(),
                source.emotionAdaptiveVoice(),
                source.agentPersona(),
                source.languageVoices(),
                source.sipTrunkIdsOrEmpty());
        long newId = campaigns.create(row);
        audit.record("CAMPAIGN_CLONE", "campaign", String.valueOf(newId), "from " + id);
        return campaignRow(newId);
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

    private Map<String, String> requireKnownVoicePerLanguage(long companyId, Map<String, String> languageVoices) {
        if (languageVoices.isEmpty()) {
            return Map.of();
        }
        Map<String, String> checked = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : languageVoices.entrySet()) {
            String language = companyConfig.resolveLanguage(companyId, e.getKey());
            String voiceId = requireKnownVoice(e.getValue());
            if (voiceId == null) {
                continue;
            }
            TtsVoice voice = voices.find(voiceId);
            if (voice != null && !language.equalsIgnoreCase(voice.language())) {
                throw new ValidationException(ErrorCode.TTS_VOICE_LANGUAGE_MISMATCH,
                        voiceId, voice.language(), language);
            }
            checked.put(language, voiceId);
        }
        return checked;
    }

    private String requireKnownVoice(String ttsVoice) {
        if (ttsVoice == null || ttsVoice.isBlank()) {
            return null;
        }
        String trimmed = ttsVoice.trim();
        if (!voices.isSelectable(trimmed)) {
            throw new ValidationException(ErrorCode.TTS_VOICE_UNKNOWN, trimmed, voices.selectableIds());
        }
        return trimmed;
    }

    @Override
    public long addTarget(long campaignId, long clientId, String phone, String language, JsonNode contextData) {
        String json = contextData != null && !contextData.isNull() ? contextData.toString() : "{}";
        return targets.add(campaignId, clientId, PhoneNumbers.require(phone), language, json);
    }

    @Override
    public AddTargetsResponse addTargets(long campaignId, List<AddTargetRequest> requests) {
        List<Long> ids = requests.stream()
                .map(t -> addTarget(campaignId, t.clientId(), t.phone(), t.language(), t.contextData()))
                .toList();
        return new AddTargetsResponse(campaignId, ids.size(), ids);
    }

    @Override
    public TargetImportResult importTargetsCsv(long campaignId, String csv) {
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<Long> added = new ArrayList<>();
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());
        for (ParsedTarget t : parsed.targets()) {
            try {
                added.add(targets.add(campaignId, t.clientId(), PhoneNumbers.require(t.phone()),
                        t.language(), t.contextJson()));
            } catch (Exception e) {
                errors.add(new CsvRowError(t.line(), e.getMessage()));
            }
        }
        audit.record("TARGETS_IMPORT", "campaign", String.valueOf(campaignId),
                added.size() + " added, " + errors.size() + " rejected");
        log.info("CSV import into campaign {}: {} added, {} rejected, unknown columns {}",
                campaignId, added.size(), errors.size(), parsed.unknownColumns());
        return new TargetImportResult(campaignId, added.size(), added, errors, parsed.unknownColumns());
    }

    @Override
    public TargetCsvPreview previewTargetsCsv(long campaignId, String csv) {
        requireCampaign(campaignId);
        List<CsvColumnMapping> columns = TargetCsvImporter.mapColumns(csv);
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<ParsedTarget> sample = parsed.targets().stream().limit(10).toList();
        return new TargetCsvPreview(columns, sample, parsed.targets().size(), parsed.errors(), parsed.unknownColumns());
    }

    @Override
    public PageableData<CampaignRow> filterCampaigns(CampaignFilter filter) {
        List<Campaign> rows = campaigns.findAll(filter);
        long total = campaigns.count(filter);
        return PageableData.of(toRows(rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public PageableData<CampaignRow> listCampaigns(CampaignFilter filter) {
        return filterCampaigns(filter);
    }

    private List<CampaignRow> toRows(List<Campaign> rows) {
        Set<Long> campaignIds = rows.stream().map(Campaign::id).collect(Collectors.toSet());
        Map<Long, CampaignTargetStats> statsMap = targets.statsByCampaignIds(campaignIds);
        Map<Long, String> scenarioNames = scenarios.scenarioNamesByIds(
                rows.stream().map(Campaign::scenarioId).collect(Collectors.toSet()));
        Map<Long, String> creatorNames = users.namesByIds(
                rows.stream().map(Campaign::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> CampaignRow.of(c, scenarioNames.get(c.scenarioId()),
                        c.createdBy() != null ? creatorNames.get(c.createdBy()) : null,
                        statsMap.getOrDefault(c.id(), CampaignTargetStats.ZERO)))
                .toList();
    }

    @Override
    public CampaignRow campaignRow(long id) {
        Campaign c = requireCampaign(id);
        String scenarioName = scenarios.scenarioNamesByIds(List.of(c.scenarioId())).get(c.scenarioId());
        String createdByName = c.createdBy() != null
                ? users.namesByIds(List.of(c.createdBy())).get(c.createdBy())
                : null;
        CampaignTargetStats stats = targets.statsByCampaignId(id);
        return CampaignRow.of(c, scenarioName, createdByName, stats);
    }

    @Override
    public Campaign getCampaign(long id) {
        return campaigns.find(id);
    }

    @Override
    public Campaign requireCampaign(long id) {
        Campaign campaign = getCampaign(id);
        if (campaign == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
        }
        return campaign;
    }

    @Override
    public PageableData<CampaignTarget> listTargets(long campaignId, TargetFilter filter) {
        List<CampaignTarget> rows = targets.findByCampaign(campaignId, filter);
        long total = targets.countByCampaign(campaignId);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    public void setStatus(long companyId, long campaignId, CampaignStatus status) {
        campaigns.updateStatus(companyId, campaignId, status);
        audit.record("CAMPAIGN_" + status.name(), "campaign", String.valueOf(campaignId), null);
    }

    @Override
    public CampaignStatusResponse start(long campaignId, boolean immediate) {
        setStatus(currentCompany.id(), campaignId, CampaignStatus.ACTIVE);
        if (immediate) {
            // A target waiting on a retry interval — or on a pause that lasted past its
            // slot — stays unclaimable until its own time comes, so a resumed campaign can
            // look active and dial nothing for hours. Clearing the booked time makes the
            // waiting targets due at the next tick. The dial window still governs: this
            // says "as soon as calling is allowed", never "call outside the window".
            int released = targets.clearSchedule(campaignId);
            audit.record("CAMPAIGN_START_IMMEDIATE", "campaign", String.valueOf(campaignId),
                    released + " targets released");
        }
        ttsWarmup.warmUpForCampaign(campaignId);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ACTIVE);
    }

    @Override
    public CampaignStatusResponse pause(long campaignId) {
        setStatus(currentCompany.id(), campaignId, CampaignStatus.PAUSED);
        return new CampaignStatusResponse(campaignId, CampaignStatus.PAUSED);
    }

    @Override
    public CampaignStatusResponse archive(long campaignId) {
        requireCampaign(campaignId);
        setStatus(currentCompany.id(), campaignId, CampaignStatus.ARCHIVED);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ARCHIVED);
    }

    @Override
    @Transactional
    public void doNotCall(long targetId) {
        CampaignTarget t = targets.find(targetId);
        if (t != null) {
            doNotCallList.add(currentCompany.id(), t.phone(), "opted out via API", DoNotCallSource.MANUAL);
        }
        targets.setDoNotCall(targetId);
        audit.record("TARGET_DO_NOT_CALL", "target", String.valueOf(targetId),
                t != null ? t.phone() : null);
    }

    @Override
    public DoNotCallResponse markDoNotCall(long targetId) {
        doNotCall(targetId);
        return new DoNotCallResponse(targetId, true);
    }

    @Override
    public TargetMemoryDto getTargetMemory(long targetId) {
        CampaignTarget t = targets.find(targetId);
        if (t == null) {
            throw new NotFoundException(ErrorCode.TARGET_NOT_FOUND, targetId);
        }
        Map<String, Object> map = parseContextMap(t.contextData());
        String operatorNotes = map.get("operatorNotes") != null ? map.get("operatorNotes").toString() : null;
        String lastCallSummary = map.get("lastCallSummary") != null ? map.get("lastCallSummary").toString() : null;
        String preferredName = map.get("preferredName") != null ? map.get("preferredName").toString() : null;
        return new TargetMemoryDto(targetId, operatorNotes, lastCallSummary, preferredName, map);
    }

    @Override
    @Transactional
    public TargetMemoryDto updateTargetMemory(long targetId, UpdateTargetMemoryRequest r) {
        CampaignTarget t = targets.find(targetId);
        if (t == null) {
            throw new NotFoundException(ErrorCode.TARGET_NOT_FOUND, targetId);
        }
        Map<String, Object> map = parseContextMap(t.contextData());
        if (r.operatorNotes() != null) {
            if (r.operatorNotes().isBlank()) {
                map.remove("operatorNotes");
            } else {
                map.put("operatorNotes", r.operatorNotes().trim());
            }
        }
        if (r.lastCallSummary() != null) {
            if (r.lastCallSummary().isBlank()) {
                map.remove("lastCallSummary");
            } else {
                map.put("lastCallSummary", r.lastCallSummary().trim());
            }
        }
        if (r.preferredName() != null) {
            if (r.preferredName().isBlank()) {
                map.remove("preferredName");
            } else {
                map.put("preferredName", r.preferredName().trim());
            }
        }
        if (r.additionalContext() != null) {
            map.putAll(r.additionalContext());
        }
        try {
            String updatedJson = MAPPER.writeValueAsString(map);
            targets.updateContextData(targetId, updatedJson);
            audit.record("TARGET_MEMORY_UPDATE", "target", String.valueOf(targetId), "Memory updated by operator");
            return new TargetMemoryDto(
                    targetId,
                    (String) map.get("operatorNotes"),
                    (String) map.get("lastCallSummary"),
                    (String) map.get("preferredName"),
                    map
            );
        } catch (Exception e) {
            throw new ValidationException(ErrorCode.INVALID_PARAMETER_VALUE, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContextMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Override
    @Transactional
    public void triggerRecurrenceRun(long campaignId, boolean resetTargets) {
        if (resetTargets) {
            targets.resetTargetsForRecurrence(campaignId);
            log.info("Reset targets for recurring campaign {}", campaignId);
        }
        campaigns.recordRecurrenceRun(campaignId, Instant.now(clock), CampaignStatus.ACTIVE);
        ttsWarmup.warmUpForCampaign(campaignId);
        log.info("Triggered recurring run for campaign {}", campaignId);
    }

    @Override
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

    @Override
    public void scheduleCallback(long targetId, Instant callbackAt) {
        CampaignTarget t = targets.find(targetId);
        if (t == null || callbackAt == null) {
            return;
        }
        Campaign c = campaigns.find(t.campaignId());
        ZonedDateTime candidate = callbackAt.atZone(ZoneId.of("Asia/Tashkent"));
        RetryProperties retry = dialerProps.retry();
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
        Duration delay = RetrySchedule.delayFor(disposition, dialerProps.retry(), retryMinutes);
        ZonedDateTime candidate = ZonedDateTime.now(clock).plus(delay);
        RetryProperties retry = dialerProps.retry();
        if (campaign == null || retry == null || !retry.respectDialWindow()) {
            return candidate.toInstant();
        }
        return RetrySchedule.intoWindow(candidate, campaign.allowedDays(),
                campaign.dialWindowStart(), campaign.dialWindowEnd()).toInstant();
    }

    private void checkCompletion(long campaignId) {
        Campaign c = campaigns.find(campaignId);
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
