package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.DebtorSourcePort;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.*;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignStateMachine;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;
import uz.murodjon.robotcallv2.campaign.domain.service.RetrySchedule;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CampaignService implements CampaignUseCase, CampaignTargetUseCase {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    /** Only used to build a target's {@code context_data}; no configuration of its own. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final DoNotCallUseCase doNotCallUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final UserUseCase userUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final RetryProperties retryProperties;
    private final AuditUseCase auditUseCase;
    private final NotificationUseCase notificationUseCase;
    private final TtsWarmup ttsWarmup;
    private final TargetSourceRepository targetSourceRepository;
    private final TargetApiImporter targetApiImporter;
    private final ChainedTargetImporter chainedTargetImporter;
    private final DebtorSourcePort debtorSource;
    private final CampaignValidator campaignValidator;
    private final SecretCipher secretCipher;
    private final Clock clock;

    public CampaignService(CampaignRepository campaignRepository, CampaignTargetRepository campaignTargetRepository,
                           DoNotCallUseCase doNotCallUseCase, AiAgentUseCase aiAgentUseCase,
                           UserUseCase userUseCase, CompanyConfigUseCase companyConfigUseCase,
                           RetryProperties retryProperties, AuditUseCase auditUseCase, NotificationUseCase notificationUseCase,
                           TtsWarmup ttsWarmup, TargetSourceRepository targetSourceRepository,
                           TargetApiImporter targetApiImporter, ChainedTargetImporter chainedTargetImporter,
                           DebtorSourcePort debtorSource, CampaignValidator campaignValidator,
                           SecretCipher secretCipher, Clock clock
    ) {
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.doNotCallUseCase = doNotCallUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.userUseCase = userUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.retryProperties = retryProperties;
        this.auditUseCase = auditUseCase;
        this.notificationUseCase = notificationUseCase;
        this.ttsWarmup = ttsWarmup;
        this.targetSourceRepository = targetSourceRepository;
        this.targetApiImporter = targetApiImporter;
        this.chainedTargetImporter = chainedTargetImporter;
        this.debtorSource = debtorSource;
        this.campaignValidator = campaignValidator;
        this.secretCipher = secretCipher;
        this.clock = clock;
    }

    private static final Set<DayOfWeek> DEFAULT_DIAL_DAYS = EnumSet.allOf(DayOfWeek.class);

    @Override
    public CreateCampaignResponse createCampaign(long companyId, CreateCampaignRequest request) {
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, request.aiAgentId());
        int dailyCallCap = Math.max(0, request.dailyCallCap());
        LocalTime dialWindowStart = request.dialWindowStart() != null ? request.dialWindowStart() : LocalTime.of(9, 0);
        LocalTime dialWindowEnd = request.dialWindowEnd() != null ? request.dialWindowEnd() : LocalTime.of(20, 0);
        requireWindowWithinCompany(companyId, dialWindowStart, dialWindowEnd);
        Campaign row = new Campaign(
                0,
                request.name(),
                request.type(),
                CampaignStatus.DRAFT,
                dialWindowStart,
                dialWindowEnd,
                request.dialDays() != null && !request.dialDays().isEmpty() ? request.dialDays() : DEFAULT_DIAL_DAYS,
                request.maxAttempts() > 0 ? request.maxAttempts() : 3,
                Math.max(0, request.retryIntervalMinutes()),
                request.maxConcurrentCalls() > 0 ? request.maxConcurrentCalls() : 20,
                dailyCallCap,
                agent.id(),
                companyId,
                null,
                request.recurrenceTypeOrDefault(),
                request.recurringDayOfMonth(),
                request.cronExpression(),
                request.autoResetTargetsOrDefault(),
                null,
                request.priorityOrDefault(),
                request.retryPolicyOrNone(),
                request.startAt(),
                request.endAt(),
                request.budgetUzs(),
                request.dailyBudgetUzs());
        requireSchedule(request.startAt(), request.endAt());
        long id = campaignRepository.create(companyId, row);
        auditUseCase.record(companyId, "CAMPAIGN_CREATE", "campaign", String.valueOf(id),
                request.name() + " (agent " + agent.name() + ", cap/day=" + dailyCallCap
                        + ", recurrence=" + request.recurrenceTypeOrDefault() + ")");
        return new CreateCampaignResponse(id, CampaignStatus.DRAFT);
    }

    @Override
    public CampaignRow updateCampaign(long companyId, long id, UpdateCampaignRequest request) {
        Campaign existing = requireCampaign(companyId, id);
        AiAgent agent = aiAgentUseCase.requireAgent(existing.companyId(), request.aiAgentId());
        requireWindowWithinCompany(existing.companyId(), request.dialWindowStart(), request.dialWindowEnd());
        Campaign row = new Campaign(
                id,
                request.name(),
                existing.type(),
                existing.status(),
                request.dialWindowStart(),
                request.dialWindowEnd(),
                request.dialDays(),
                request.maxAttempts(),
                Math.max(0, request.retryIntervalMinutes()),
                request.maxConcurrentCalls(),
                Math.max(0, request.dailyCallCap()),
                agent.id(),
                existing.companyId(),
                existing.createdBy(),
                request.recurrenceTypeOrDefault(),
                request.recurringDayOfMonth(),
                request.cronExpression(),
                request.autoResetTargetsOrDefault(),
                existing.lastRunAt(),
                request.priorityOrDefault(),
                request.retryPolicyOrNone(),
                request.startAt(),
                request.endAt(),
                request.budgetUzs(),
                request.dailyBudgetUzs(),
                // Not sent: the version just read, so a client that predates it edits as before.
                request.version() != null ? request.version() : existing.version());
        requireSchedule(request.startAt(), request.endAt());
        if (!campaignRepository.update(companyId, id, row)) {
            throw new ConflictException(ErrorCode.ENTITY_VERSION_CONFLICT, "campaign", id);
        }
        auditUseCase.record(companyId, "CAMPAIGN_UPDATE", "campaign", String.valueOf(id), request.name());
        return requireCampaignRow(companyId, id);
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
                null,
                source.priority(),
                source.retryPolicy(),
                source.startAt(),
                source.endAt(),
                source.budgetUzs(),
                source.dailyBudgetUzs());
        long newId = campaignRepository.create(companyId, row);
        auditUseCase.record(companyId, "CAMPAIGN_CLONE", "campaign", String.valueOf(newId), "from " + id);
        return requireCampaignRow(companyId, newId);
    }

    private void requireWindowWithinCompany(long companyId, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return;
        }
        CompanyConfig config = companyConfigUseCase.find(companyId);
        if (config == null || config.dialWindowStart() == null || config.dialWindowEnd() == null) {
            return;
        }
        if (start.isBefore(config.dialWindowStart()) || end.isAfter(config.dialWindowEnd())) {
            throw new ValidationException(ErrorCode.CAMPAIGN_DIAL_WINDOW_OUT_OF_RANGE,
                    start, end, config.dialWindowStart(), config.dialWindowEnd());
        }
    }

    /**
     * One target, idempotently: a number the campaign already has queued is not queued
     * again, and the existing target's id comes back — a second callback request from the
     * same caller, or an instant call to a number already waiting, reuses the row.
     */
    @Override
    public long addTarget(long companyId, long campaignId, long clientId, String phone, String language,
                          JsonNode contextData) {
        String json = contextData != null && !contextData.isNull() ? contextData.toString() : "{}";
        String normalized = PhoneNumbers.require(phone);
        long id = campaignTargetRepository.add(companyId, CampaignTarget.queued(campaignId, clientId, normalized, language, json));
        if (id != 0) {
            return id;
        }
        CampaignTarget existing = campaignTargetRepository.findQueued(companyId, campaignId, normalized);
        return existing != null ? existing.id() : 0;
    }

    @Override
    public AddTargetsResponse addTargets(long companyId, long campaignId, List<AddTargetRequest> requests) {
        // The rows are written by campaign id alone, so the campaign has to be this company's.
        requireCampaign(companyId, campaignId);
        TargetIntake intake = new TargetIntake(companyId, campaignId);
        for (AddTargetRequest t : requests) {
            String json = t.contextData() != null && !t.contextData().isNull() ? t.contextData().toString() : "{}";
            intake.add(t.clientId(), PhoneNumbers.require(t.phone()), t.language(), json);
        }
        intake.insert();
        return new AddTargetsResponse(campaignId, intake.added.size(), intake.added, intake.duplicates,
                intake.doNotCall);
    }

    @Override
    public TargetImportResult importTargetsCsv(long companyId, long campaignId, String csv) {
        requireCampaign(companyId, campaignId);
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        TargetIntake intake = new TargetIntake(companyId, campaignId);
        List<CsvRowError> errors = new ArrayList<>(parsed.errors());
        for (ParsedTarget t : parsed.targets()) {
            try {
                intake.add(t.clientId(), PhoneNumbers.require(t.phone()), t.language(), t.contextJson());
            } catch (Exception e) {
                errors.add(new CsvRowError(t.line(), e.getMessage()));
            }
        }
        intake.insert();
        auditUseCase.record(companyId, "TARGETS_IMPORT", "campaign", String.valueOf(campaignId),
                intake.summary() + ", " + errors.size() + " rejected");
        log.info("CSV import into campaign {}: {}, {} rejected, unknown columns {}",
                campaignId, intake.summary(), errors.size(), parsed.unknownColumns());
        return new TargetImportResult(campaignId, intake.added.size(), intake.added, intake.duplicates,
                intake.doNotCall, errors, parsed.unknownColumns());
    }

    /**
     * One import's bookkeeping. Every number goes through the same two checks: a number
     * the company's do-not-call list holds is stored marked (the campaign's counts show
     * it, the dialer never claims it), and a number the campaign already has queued is
     * left alone rather than queued twice.
     *
     * <p>{@link #add} only collects; {@link #insert} then asks the do-not-call list about
     * every number at once and writes the rows in batches. A 10 000-row file used to be
     * 10 000 lookups and 10 000 INSERTs.
     */
    private final class TargetIntake {
        private final long companyId;
        private final long campaignId;
        private final List<CampaignTarget> accepted = new ArrayList<>();
        private final List<Long> added = new ArrayList<>();
        private int duplicates;
        private int doNotCall;
        private Set<String> queued;

        private TargetIntake(long companyId, long campaignId) {
            this.companyId = companyId;
            this.campaignId = campaignId;
        }

        private void add(long clientId, String phone, String language, String contextJson) {
            // The campaign's queued numbers are read once per import, not once per row: a
            // per-row lookup made a 10 000-row file 10 000 extra queries. The unique index
            // still has the last word on a number queued by someone else meanwhile.
            if (queued == null) {
                queued = new HashSet<>(campaignTargetRepository.findQueuedPhones(companyId, campaignId));
            }
            if (!queued.add(phone)) {
                duplicates++;
                return;
            }
            accepted.add(CampaignTarget.queued(campaignId, clientId, phone, language, contextJson));
        }

        /** Writes what {@link #add} accepted: one do-not-call lookup, then batched inserts. */
        private void insert() {
            if (accepted.isEmpty()) {
                return;
            }
            Set<String> blocked = doNotCallUseCase.findBlockedPhones(companyId,
                    accepted.stream().map(CampaignTarget::phone).toList());
            List<CampaignTarget> rows = accepted.stream()
                    .map(t -> CampaignTarget.queued(campaignId, t.clientId(), t.phone(), t.language(),
                            t.contextData(), blocked.contains(t.phone())))
                    .toList();
            accepted.clear();
            Map<String, Long> ids = campaignTargetRepository.insertAllQueued(companyId, rows);
            for (CampaignTarget row : rows) {
                Long id = ids.get(row.phone());
                if (id == null) {
                    // Queued by someone else after the queued numbers were read.
                    duplicates++;
                    continue;
                }
                added.add(id);
                if (row.doNotCall()) {
                    doNotCall++;
                }
            }
        }

        private String summary() {
            return added.size() + " added, " + duplicates + " duplicates skipped, " + doNotCall + " do-not-call";
        }
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
        TargetSource source = targetSourceRepository.findByCampaignId(campaignId);
        return source != null ? TargetSourceRow.of(source) : null;
    }

    @Override
    public TargetSourceRow updateTargetSource(long companyId, long campaignId, UpdateTargetSourceRequest request) {
        requireCampaign(companyId, campaignId);
        TargetSource existing = targetSourceRepository.findByCampaignId(campaignId);
        // Omitted on an edit means "keep the secret you already have" — the API never
        // hands it back out, so a client re-saving the form has nothing to send back.
        String authHeaderValue = request.authHeaderValue() != null && !request.authHeaderValue().isBlank()
                ? encryptSecret(request.authHeaderValue())
                : (existing != null ? existing.authHeaderValue() : null);
        // UYSOT_DEBTORS is not offered here: it is provisioned, reads its credentials from
        // the company's CRM connection rather than from this form, and would otherwise be
        // settable on a campaign whose company has no such connection.
        TargetSourceProvider provider = request.providerOrDefault() == TargetSourceProvider.CHAINED
                ? TargetSourceProvider.CHAINED
                : TargetSourceProvider.GENERIC;
        campaignValidator.validateTargetSource(provider, request.url(), request.steps());
        TargetSource saved = targetSourceRepository.upsert(campaignId, new TargetSource(
                campaignId,
                provider,
                request.url() != null ? request.url().trim() : null,
                request.method(),
                request.requestBody(),
                request.authHeaderName(),
                authHeaderValue,
                request.itemsPath(),
                request.phoneField(),
                request.clientIdField(),
                request.languageField(),
                request.replaceTargets() != null && request.replaceTargets(),
                request.syncOnRecurrence() == null || request.syncOnRecurrence(),
                request.enabled() == null || request.enabled(),
                request.steps(),
                existing != null ? existing.lastSyncAt() : null,
                existing != null ? existing.lastSyncAdded() : null,
                existing != null ? existing.lastSyncError() : null));
        auditUseCase.record(companyId, "TARGET_SOURCE_UPDATE", "campaign", String.valueOf(campaignId),
                provider == TargetSourceProvider.CHAINED
                        ? provider + " (" + saved.steps().size() + " step(s))"
                        : saved.url());
        return TargetSourceRow.of(saved);
    }

    @Override
    public void deleteTargetSource(long companyId, long campaignId) {
        requireCampaign(companyId, campaignId);
        targetSourceRepository.delete(campaignId);
        auditUseCase.record(companyId, "TARGET_SOURCE_DELETE", "campaign", String.valueOf(campaignId), null);
    }

    @Override
    public TargetSyncResult syncTargetsFromSource(long companyId, long campaignId) {
        requireCampaign(companyId, campaignId);
        TargetSource source = targetSourceRepository.findByCampaignId(campaignId);
        if (source == null) {
            throw new NotFoundException(ErrorCode.TARGET_SOURCE_NOT_FOUND, campaignId);
        }
        // enabled is off for both routes, not only the recurrence sweep: a source is
        // switched off because calling it is unwanted — half-configured, pointed at the
        // wrong environment, or a template nobody has filled in yet — and a hand-run sync
        // over replaceTargets would empty the campaign just as thoroughly as an automatic one.
        if (!source.enabled()) {
            throw new ConflictException(ErrorCode.TARGET_SOURCE_DISABLED, campaignId);
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
            fetched = switch (source.providerOrDefault()) {
                case UYSOT_DEBTORS -> fetchDebtors(companyId);
                case CHAINED -> chainedTargetImporter.fetchTargets(
                        source, decryptSecret(source.authHeaderValue()), errors);
                case GENERIC -> targetApiImporter.fetchTargets(
                        source, decryptSecret(source.authHeaderValue()), errors);
            };
        } catch (RuntimeException e) {
            targetSourceRepository.recordSync(campaignId, Instant.now(clock), 0, e.getMessage());
            notificationUseCase.notify(companyId, NotificationType.TARGET_SYNC_FAILED, "Nishonlar sinxronlanmadi",
                    "Kampaniya " + campaignId + " uchun target source javob bermadi: " + e.getMessage(), null);
            throw e;
        }
        // Only once the endpoint has answered: clearing first would empty the campaign on
        // every failed fetch, and a campaign with no targets dials nobody all day.
        // "Replace" retires the old list without touching what was already dialled: targets
        // never dialled are deleted, ones waiting on a retry are closed as EXHAUSTED, and a
        // call in progress or a finished target stays — its attempts and results are history,
        // and call_attempt rows point at it.
        int removed = source.replaceTargets()
                ? campaignTargetRepository.deleteUndialedByCampaignId(companyId, campaignId)
                + campaignTargetRepository.exhaustPendingByCampaignId(companyId, campaignId)
                : 0;
        TargetIntake intake = new TargetIntake(companyId, campaignId);
        for (ParsedTarget t : fetched) {
            try {
                intake.add(t.clientId(), PhoneNumbers.require(t.phone()), t.language(), t.contextJson());
            } catch (Exception e) {
                errors.add(new CsvRowError(t.line(), e.getMessage()));
            }
        }
        intake.insert();
        targetSourceRepository.recordSync(campaignId, Instant.now(clock), intake.added.size(), null);
        auditUseCase.record(companyId, "TARGETS_SYNC", "campaign", String.valueOf(campaignId),
                intake.summary() + ", " + removed + " removed, " + errors.size() + " rejected");
        log.info("Target source sync for campaign {}: {} fetched, {}, {} removed, {} rejected",
                campaignId, fetched.size(), intake.summary(), removed, errors.size());
        return new TargetSyncResult(campaignId, fetched.size(), intake.added.size(), removed,
                intake.duplicates, intake.doNotCall, errors);
    }

    /**
     * Today's debtors as targets. The facts are keyed by the debt-collection scenario's
     * {@code factSchema} names — {@code clientName}, {@code debtAmount}, {@code currency},
     * {@code contractNumber} — because that schema is what decides which of them the agent
     * is allowed to say out loud. {@code client_id} carries the Uysot lead id, which is
     * what the post-call note is attached to once the conversation ends.
     */
    private List<ParsedTarget> fetchDebtors(long companyId) {
        List<Debtor> debtors = debtorSource.findOverdueDebtors(companyId, 0);
        List<ParsedTarget> parsed = new ArrayList<>(debtors.size());
        int line = 0;
        for (Debtor debtor : debtors) {
            line++;
            ObjectNode facts = JSON.createObjectNode();
            facts.put("clientName", debtor.name());
            facts.put("debtAmount", debtor.debtAmount());
            facts.put("currency", debtor.currency());
            facts.put("contractNumber", debtor.contractNumber());
            parsed.add(new ParsedTarget(line, debtor.leadId(), debtor.phone(), null, facts.toString()));
        }
        return parsed;
    }

    private String encryptSecret(String plaintext) {
        if (!secretCipher.available()) {
            // This deployment's own configuration is missing, not a vendor that fell over.
            throw new ConflictException(ErrorCode.ENCRYPTION_KEY_NOT_SET);
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
        List<Campaign> rows = campaignRepository.findAll(companyId, filter);
        long total = campaignRepository.countByFilter(companyId, filter);
        return PageableData.of(toRows(companyId, rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public PageableData<CampaignRow> listCampaigns(long companyId, CampaignFilter filter) {
        return filterCampaigns(companyId, filter);
    }

    private List<CampaignRow> toRows(long companyId, List<Campaign> rows) {
        Set<Long> campaignIds = rows.stream().map(Campaign::id).collect(Collectors.toSet());
        Map<Long, CampaignTargetStats> statsMap = campaignTargetRepository.findStatsByCampaignIds(companyId, campaignIds);
        Map<Long, String> agentNames = aiAgentUseCase.findNamesByIds(
                rows.stream().map(Campaign::aiAgentId).collect(Collectors.toSet()));
        Map<Long, String> creatorNames = userUseCase.findNamesByIds(companyId,
                rows.stream().map(Campaign::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> CampaignRow.of(c, agentNames.get(c.aiAgentId()),
                        c.createdBy() != null ? creatorNames.get(c.createdBy()) : null,
                        statsMap.getOrDefault(c.id(), CampaignTargetStats.ZERO)))
                .toList();
    }

    @Override
    public CampaignRow requireCampaignRow(long companyId, long id) {
        Campaign c = requireCampaign(companyId, id);
        String agentName = aiAgentUseCase.findNamesByIds(List.of(c.aiAgentId())).get(c.aiAgentId());
        String createdByName = c.createdBy() != null
                ? userUseCase.findNamesByIds(companyId, List.of(c.createdBy())).get(c.createdBy())
                : null;
        CampaignTargetStats stats = campaignTargetRepository.findStatsByCampaignId(companyId, id);
        return CampaignRow.of(c, agentName, createdByName, stats);
    }

    @Override
    public Campaign findCampaign(long companyId, long id) {
        return campaignRepository.find(companyId, id);
    }

    @Override
    public Campaign requireCampaign(long companyId, long id) {
        Campaign campaign = findCampaign(companyId, id);
        if (campaign == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
        }
        return campaign;
    }

    @Override
    public PageableData<CampaignTarget> listTargets(long companyId, long campaignId, TargetFilter filter) {
        List<CampaignTarget> rows = campaignTargetRepository.findByCampaign(companyId, campaignId, filter);
        long total = campaignTargetRepository.countByCampaign(companyId, campaignId);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public CampaignTarget requireTarget(long companyId, long campaignId, long targetId) {
        CampaignTarget target = campaignTargetRepository.find(companyId, targetId);
        if (target == null || target.campaignId() != campaignId) {
            throw new NotFoundException(ErrorCode.TARGET_NOT_FOUND, targetId);
        }
        return target;
    }

    /** Every status change goes through here, and through {@link CampaignStateMachine}. */
    public void setStatus(long companyId, long campaignId, CampaignStatus status) {
        Campaign current = requireCampaign(companyId, campaignId);
        if (current.status() == status) {
            return;
        }
        CampaignStateMachine.requireTransition(campaignId, current.status(), status);
        campaignRepository.updateStatus(companyId, campaignId, status);
        auditUseCase.record(companyId, "CAMPAIGN_" + status.name(), "campaign", String.valueOf(campaignId), null);
    }

    /**
     * The dialer found the campaign cannot place a call at all — no usable trunk — and
     * parked it so the owner sees a paused campaign with a reason, not a running one that
     * dials nothing. Unlike {@link #pause} this is called off a scheduler thread, for any
     * company.
     */
    @Override
    public void pauseByPlatform(long companyId, long campaignId, String reason) {
        Campaign c = campaignRepository.find(companyId, campaignId);
        if (c == null || c.status() != CampaignStatus.ACTIVE) {
            return;
        }
        setStatus(companyId, campaignId, CampaignStatus.PAUSED);
        notificationUseCase.notify(companyId, NotificationType.CAMPAIGN_PAUSED, "Kampaniya to'xtatildi",
                "\"" + c.name() + "\" kampaniyasi to'xtatildi: " + reason, null);
    }

    /**
     * Gives a claimed target back — the attempt claimDue counted included — because nothing
     * was dialled for it after all (the campaign stopped, or its window closed, while the
     * task waited in the queue).
     */
    @Override
    public void releaseClaim(long targetId, Instant nextAttemptAt) {
        campaignTargetRepository.releaseClaim(targetId, nextAttemptAt);
    }

    @Override
    public List<Campaign> findActiveCampaigns() {
        return campaignRepository.findActive();
    }

    @Override
    public List<Campaign> findCampaignsByName(long companyId, String query, int limit) {
        return campaignRepository.searchByName(companyId, query, limit);
    }

    @Override
    public CampaignTarget findTarget(long companyId, long targetId) {
        return campaignTargetRepository.find(companyId, targetId);
    }

    @Override
    public CampaignTarget findQueuedTarget(long companyId, long campaignId, String phone) {
        return campaignTargetRepository.findQueued(companyId, campaignId, phone);
    }

    @Override
    public long addQueuedTarget(long companyId, CampaignTarget target) {
        return campaignTargetRepository.add(companyId, target);
    }

    @Override
    public void updateTargetStatus(long targetId, TargetStatus status, Instant nextAttemptAt) {
        campaignTargetRepository.updateStatus(targetId, status, nextAttemptAt);
    }

    @Override
    public List<CampaignTarget> claimDueTargets(long campaignId, int limit) {
        return campaignTargetRepository.claimDue(campaignId, limit);
    }

    @Override
    public Map<Long, Long> findCompanyIdsOfExpiredClaims(Instant claimedBefore) {
        return campaignTargetRepository.findCompanyIdsOfExpiredClaims(claimedBefore);
    }

    /** The dialer is holding this campaign's calls for want of balance. */
    @Override
    public void notifyBalanceExhausted(long companyId, long campaignId) {
        Campaign c = campaignRepository.find(companyId, campaignId);
        String name = c != null ? c.name() : String.valueOf(campaignId);
        notificationUseCase.notify(companyId, NotificationType.BALANCE_LOW, "Balans yetarli emas",
                "\"" + name + "\" kampaniyasining qo'ng'iroqlari balans to'ldirilguncha kutmoqda", null);
    }

    @Override
    public CampaignStatusResponse start(long companyId, long campaignId, boolean immediate) {
        Campaign c = requireCampaign(companyId, campaignId);
        // Every dialer tick skips a campaign whose agent is disabled, so it would show
        // ACTIVE and never ring anyone.
        if (!aiAgentUseCase.requireAgent(companyId, c.aiAgentId()).enabled()) {
            throw new ConflictException(ErrorCode.AI_AGENT_DISABLED, c.aiAgentId());
        }
        // A campaign with nothing to dial and no source that will fill it is not started,
        // it is shown ACTIVE and never rings anyone.
        if (campaignTargetRepository.countByCampaign(companyId, campaignId) == 0 && targetSourceRepository.findByCampaignId(campaignId) == null) {
            throw new ConflictException(ErrorCode.CAMPAIGN_HAS_NO_TARGETS, campaignId);
        }
        Instant now = clock.instant();
        if (c.endAt() != null && !c.endAt().isAfter(now)) {
            throw new ConflictException(ErrorCode.CAMPAIGN_END_PASSED, campaignId, c.endAt());
        }
        // A future startAt parks the campaign; "immediate" overrides it — the operator is
        // standing at the button and wants it now, whatever the plan said.
        if (!immediate && c.startAt() != null && c.startAt().isAfter(now)) {
            setStatus(companyId, campaignId, CampaignStatus.SCHEDULED);
            return new CampaignStatusResponse(campaignId, CampaignStatus.SCHEDULED);
        }
        setStatus(companyId, campaignId, CampaignStatus.ACTIVE);
        if (immediate) {
            // A target waiting on a retry interval — or on a pause that lasted past its
            // slot — stays unclaimable until its own time comes, so a resumed campaign can
            // look active and dial nothing for hours. Clearing the booked time makes the
            // waiting targets due at the next tick. The dial window still governs: this
            // says "as soon as calling is allowed", never "call outside the window".
            int released = campaignTargetRepository.clearSchedule(campaignId);
            auditUseCase.record(companyId, "CAMPAIGN_START_IMMEDIATE", "campaign", String.valueOf(campaignId),
                    released + " targets released");
        }
        ttsWarmup.warmUpForCampaign(c);
        return new CampaignStatusResponse(campaignId, CampaignStatus.ACTIVE);
    }

    @Override
    public void sweepScheduledCampaigns() {
        Instant now = clock.instant();
        for (Campaign c : campaignRepository.findScheduledDue(now)) {
            try {
                start(c.companyId(), c.id(), false);
                log.info("Scheduled campaign {} ({}) started at its startAt", c.id(), c.name());
            } catch (RuntimeException e) {
                log.warn("Scheduled campaign {} ({}) could not start: {}", c.id(), c.name(), e.getMessage());
            }
        }
        for (Campaign c : campaignRepository.findActiveExpired(now)) {
            try {
                setStatus(c.companyId(), c.id(), CampaignStatus.COMPLETED);
                notificationUseCase.notify(c.companyId(), NotificationType.CAMPAIGN_FINISHED, "Kampaniya yakunlandi",
                        "\"" + c.name() + "\" kampaniyasi belgilangan tugash vaqtiga yetgani uchun yakunlandi", null);
            } catch (RuntimeException e) {
                log.warn("Campaign {} ({}) could not be closed at its endAt: {}", c.id(), c.name(), e.getMessage());
            }
        }
        // Completion is otherwise checked only when a call's outcome is applied, so a
        // campaign whose last target was settled any other way (a source sync retiring the
        // old list, a restart) stayed ACTIVE for good — and a recurring one never ran again.
        // A campaign with no targets at all is left alone: its source may not have filled it yet.
        for (Campaign c : campaignRepository.findActive()) {
            try {
                if (campaignTargetRepository.countByCampaign(c.companyId(), c.id()) > 0) {
                    checkCompletion(c.companyId(), c.id());
                }
            } catch (RuntimeException e) {
                log.warn("Completion check failed for campaign {} ({}): {}", c.id(), c.name(), e.getMessage());
            }
        }
    }

    private static void requireSchedule(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new ValidationException(ErrorCode.CAMPAIGN_SCHEDULE_INVALID);
        }
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
    public DoNotCallResponse markDoNotCall(long companyId, long targetId) {
        CampaignTarget t = campaignTargetRepository.find(companyId, targetId);
        if (t != null) {
            doNotCallUseCase.add(companyId, t.phone(), "opted out via API", DoNotCallSource.MANUAL);
        }
        campaignTargetRepository.setDoNotCall(companyId, targetId);
        auditUseCase.record(companyId, "TARGET_DO_NOT_CALL", "target", String.valueOf(targetId),
                t != null ? t.phone() : null);
        return new DoNotCallResponse(targetId, true);
    }

    @Override
    @Transactional
    public void triggerRecurrenceRun(long companyId, long campaignId, boolean resetTargets) {
        // Read again: the sweep's list is a minute old, and a campaign paused since must stay paused.
        Campaign campaign = requireCampaign(companyId, campaignId);
        if (campaign.status() != CampaignStatus.COMPLETED) {
            log.info("Recurring run of campaign {} skipped: it is {}, not COMPLETED", campaignId, campaign.status());
            return;
        }
        if (resetTargets) {
            campaignTargetRepository.resetTargetsForRecurrence(campaignId);
            log.info("Reset targets for recurring campaign {}", campaignId);
        }
        syncFromSourceQuietly(companyId, campaignId);
        campaignRepository.recordRecurrenceRun(campaignId, Instant.now(clock), CampaignStatus.ACTIVE);
        ttsWarmup.warmUpForCampaign(campaign);
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
        TargetSource source = targetSourceRepository.findByCampaignId(campaignId);
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
        CampaignTarget t = campaignTargetRepository.find(companyId, targetId);
        if (t == null) {
            return;
        }
        if (isTerminal(disposition)) {
            campaignTargetRepository.updateStatus(targetId, TargetStatus.DONE, null);
            log.info("Target {} DONE ({})", targetId, disposition);
            checkCompletion(companyId, t.campaignId());
            return;
        }
        Campaign c = campaignRepository.find(companyId, t.campaignId());
        int maxAttempts = c != null ? c.maxAttempts() : 3;
        // A disposition rule may give up sooner — "one voicemail is enough" — never later
        // than the campaign's own cap.
        RetryRule rule = c != null ? c.retryPolicy().ruleFor(disposition) : null;
        if (rule != null && rule.maxAttempts() != null && rule.maxAttempts() > 0) {
            maxAttempts = Math.min(maxAttempts, rule.maxAttempts());
        }
        if (t.attempts() >= maxAttempts) {
            campaignTargetRepository.updateStatus(targetId, TargetStatus.EXHAUSTED, null);
            log.info("Target {} EXHAUSTED after {} attempts", targetId, t.attempts());
            checkCompletion(companyId, t.campaignId());
            return;
        }
        Instant next = nextAttemptAt(disposition, c);
        campaignTargetRepository.updateStatus(targetId, TargetStatus.PENDING, next);
        log.info("Target {} rescheduled ({}, attempt {}/{}) -> {}",
                targetId, disposition, t.attempts(), maxAttempts, next);
    }

    @Override
    public void scheduleCallback(long companyId, long targetId, Instant callbackAt) {
        CampaignTarget t = campaignTargetRepository.find(companyId, targetId);
        if (t == null || callbackAt == null) {
            return;
        }
        Campaign c = campaignRepository.find(companyId, t.campaignId());
        ZonedDateTime candidate = callbackAt.atZone(zoneOf(companyId));
        Instant finalInstant = candidate.toInstant();
        if (c != null && retryProperties.respectDialWindow()) {
            finalInstant = RetrySchedule.intoWindow(candidate, c.allowedDays(),
                    c.dialWindowStart(), c.dialWindowEnd(), holidaysOf(companyId)).toInstant();
        }
        campaignTargetRepository.updateStatus(targetId, TargetStatus.PENDING, finalInstant);
        log.info("Target {} scheduled for smart callback at {}", targetId, finalInstant);
    }

    /** The company's own clock — its dial window and callback times are read in it. */
    private ZoneId zoneOf(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return config != null ? config.zoneId() : clock.getZone();
    }

    /** The company's holidays, which the retry schedule steps over (NEXT-PLAN C.4). */
    private Set<LocalDate> holidaysOf(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return config != null ? Set.copyOf(config.holidays()) : Set.of();
    }

    private Instant nextAttemptAt(Disposition disposition, Campaign campaign) {
        int retryMinutes = campaign != null ? campaign.retryIntervalMinutes() : 0;
        RetryRule rule = campaign != null ? campaign.retryPolicy().ruleFor(disposition) : null;
        Duration delay = RetrySchedule.delayFor(disposition, retryProperties.toRetryDelays(), retryMinutes, rule);
        ZonedDateTime candidate = ZonedDateTime.now(clock).plus(delay);
        if (campaign == null || !retryProperties.respectDialWindow()) {
            return candidate.toInstant();
        }
        return RetrySchedule.intoWindow(candidate, campaign.allowedDays(),
                campaign.dialWindowStart(), campaign.dialWindowEnd(), holidaysOf(campaign.companyId())).toInstant();
    }

    /**
     * One statement decides: two finalizers ending the last two calls at once both used to
     * read "no active targets" and both announced the campaign finished.
     */
    private void checkCompletion(long companyId, long campaignId) {
        if (!campaignRepository.completeIfFinished(companyId, campaignId)) {
            return;
        }
        auditUseCase.record(companyId, "CAMPAIGN_COMPLETED", "campaign", String.valueOf(campaignId), null);
        Campaign c = campaignRepository.find(companyId, campaignId);
        String name = c != null ? c.name() : String.valueOf(campaignId);
        notificationUseCase.notify(companyId, NotificationType.CAMPAIGN_FINISHED,
                "Kampaniya tugadi", "\"" + name + "\" kampaniyasi barcha nishonlarni yakunladi", null);
    }

    private static boolean isTerminal(Disposition d) {
        return d == Disposition.PROMISE_TO_PAY
                || d == Disposition.REFUSED
                || d == Disposition.TRANSFERRED
                || d == Disposition.WRONG_NUMBER
                || d == Disposition.COMPLETED
                || d == Disposition.GOAL_REACHED
                || d == Disposition.GOAL_NOT_REACHED
                || d == Disposition.DO_NOT_CALL;
    }
}
