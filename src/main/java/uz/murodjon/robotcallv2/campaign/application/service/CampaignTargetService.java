package uz.murodjon.robotcallv2.campaign.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.DebtorSourcePort;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetScore;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSource;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.crm.domain.entity.Debtor;
import uz.murodjon.robotcallv2.donotcall.application.dto.DoNotCallResponse;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.csv.CsvRowError;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.shared.util.SecretCipher;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * A campaign's targets: added one by one, imported from CSV or synced from the company's
 * own API, then claimed, retried and closed by the dialer. What a finished call does to its
 * target is {@link CampaignOutcomeService}'s.
 */
@Service
public class CampaignTargetService implements CampaignTargetUseCase {

    private static final Logger log = LoggerFactory.getLogger(CampaignTargetService.class);

    /** Only used to build a target's {@code context_data}; no configuration of its own. */
    private final ObjectMapper objectMapper;

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final TargetSourceRepository targetSourceRepository;
    private final DoNotCallUseCase doNotCallUseCase;
    private final AuditUseCase auditUseCase;
    private final NotificationUseCase notificationUseCase;
    private final TargetApiImporter targetApiImporter;
    private final ChainedTargetImporter chainedTargetImporter;
    private final ToolTargetImporter toolTargetImporter;
    private final DebtorSourcePort debtorSource;
    private final CampaignValidator campaignValidator;
    private final SecretCipher secretCipher;
    private final CampaignOutcomeService campaignOutcomeService;
    private final Clock clock;

    public CampaignTargetService(CampaignRepository campaignRepository,
                                 CampaignTargetRepository campaignTargetRepository,
                                 TargetSourceRepository targetSourceRepository, DoNotCallUseCase doNotCallUseCase,
                                 AuditUseCase auditUseCase, NotificationUseCase notificationUseCase,
                                 TargetApiImporter targetApiImporter, ChainedTargetImporter chainedTargetImporter,
                                 ToolTargetImporter toolTargetImporter, DebtorSourcePort debtorSource, CampaignValidator campaignValidator,
                                 SecretCipher secretCipher, CampaignOutcomeService campaignOutcomeService, Clock clock,
                                 ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.targetSourceRepository = targetSourceRepository;
        this.doNotCallUseCase = doNotCallUseCase;
        this.auditUseCase = auditUseCase;
        this.notificationUseCase = notificationUseCase;
        this.targetApiImporter = targetApiImporter;
        this.chainedTargetImporter = chainedTargetImporter;
        this.toolTargetImporter = toolTargetImporter;
        this.debtorSource = debtorSource;
        this.campaignValidator = campaignValidator;
        this.secretCipher = secretCipher;
        this.campaignOutcomeService = campaignOutcomeService;
        this.clock = clock;
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
        requireCampaignExists(companyId, campaignId);
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
        requireCampaignExists(companyId, campaignId);
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
        requireCampaignExists(companyId, campaignId);
        List<CsvColumnMapping> columns = TargetCsvImporter.mapColumns(csv);
        TargetCsvParseResult parsed = TargetCsvImporter.parse(csv);
        List<ParsedTarget> sample = parsed.targets().stream().limit(10).toList();
        return new TargetCsvPreview(columns, sample, parsed.targets().size(), parsed.errors(), parsed.unknownColumns());
    }

    @Override
    public TargetSourceRow findTargetSource(long companyId, long campaignId) {
        requireCampaignExists(companyId, campaignId);
        TargetSource source = targetSourceRepository.findByCampaignId(campaignId);
        return source != null ? TargetSourceRow.of(source) : null;
    }

    @Override
    public TargetSourceRow updateTargetSource(long companyId, long campaignId, UpdateTargetSourceRequest request) {
        requireCampaignExists(companyId, campaignId);
        TargetSource existing = targetSourceRepository.findByCampaignId(campaignId);
        // Omitted on an edit means "keep the secret you already have" — the API never
        // hands it back out, so a client re-saving the form has nothing to send back.
        String authHeaderValue = request.authHeaderValue() != null && !request.authHeaderValue().isBlank()
                ? encryptSecret(request.authHeaderValue())
                : (existing != null ? existing.authHeaderValue() : null);
        // UYSOT_DEBTORS is not offered here: it is provisioned, reads its credentials from
        // the company's CRM connection rather than from this form, and would otherwise be
        // settable on a campaign whose company has no such connection.
        TargetSourceProvider provider = switch (request.providerOrDefault()) {
            case CHAINED -> TargetSourceProvider.CHAINED;
            case TOOL -> TargetSourceProvider.TOOL;
            default -> TargetSourceProvider.GENERIC;
        };
        campaignValidator.validateTargetSource(provider, request.url(), request.steps(), request.toolName());
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
                provider == TargetSourceProvider.TOOL ? request.toolName().trim() : null,
                provider == TargetSourceProvider.TOOL ? request.toolArguments() : null,
                existing != null ? existing.lastSyncAt() : null,
                existing != null ? existing.lastSyncAdded() : null,
                existing != null ? existing.lastSyncError() : null));
        auditUseCase.record(companyId, "TARGET_SOURCE_UPDATE", "campaign", String.valueOf(campaignId),
                switch (provider) {
                    case CHAINED -> provider + " (" + saved.steps().size() + " step(s))";
                    case TOOL -> provider + " (" + saved.toolName() + ")";
                    default -> saved.url();
                });
        return TargetSourceRow.of(saved);
    }

    @Override
    public void deleteTargetSource(long companyId, long campaignId) {
        requireCampaignExists(companyId, campaignId);
        targetSourceRepository.delete(campaignId);
        auditUseCase.record(companyId, "TARGET_SOURCE_DELETE", "campaign", String.valueOf(campaignId), null);
    }

    @Override
    public TargetSyncResult syncTargetsFromSource(long companyId, long campaignId) {
        requireCampaignExists(companyId, campaignId);
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
     * Pulls today's list before a recurring run starts, when the campaign has a source set to it.
     *
     * <p>A failure here is logged and stamped on the source rather than thrown: this runs
     * on the recurrence sweep, which serves every tenant, and one company's API being down
     * must not stop the others' campaigns from starting. The campaign then runs over the
     * list it already had — {@code replaceTargets} never clears it on a failed fetch.
     */
    void syncFromSourceQuietly(long companyId, long campaignId) {
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
                case TOOL -> toolTargetImporter.fetchTargets(companyId, source, errors);
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
            ObjectNode facts = objectMapper.createObjectNode();
            facts.put("clientName", debtor.name());
            facts.put("debtAmount", debtor.debtAmount());
            facts.put("currency", debtor.currency());
            facts.put("contractNumber", debtor.contractNumber());
            facts.put("delayDays", debtor.delayDays());
            facts.put("remainingAmount", debtor.remainingAmount());
            if (debtor.nextPaymentDate() != null) {
                facts.put("nextPaymentDate", debtor.nextPaymentDate().toString());
                facts.put("nextPaymentAmount", debtor.nextPaymentAmount());
            }
            if (debtor.responsibleName() != null) {
                facts.put("responsibleName", debtor.responsibleName());
            }
            parsed.add(new ParsedTarget(line, debtor.leadId(), debtor.phone(), null, facts.toString()));
        }
        return parsed;
    }

    @Override
    public List<Debtor> previewUysotDebtors(long companyId, int limit) {
        return debtorSource.findOverdueDebtors(companyId, Math.max(1, Math.min(limit, 50)));
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
    public void applyOutcome(long companyId, long targetId, Disposition disposition) {
        campaignOutcomeService.applyOutcome(companyId, targetId, disposition);
    }

    @Override
    public void scheduleCallback(long companyId, long targetId, Instant callbackAt) {
        campaignOutcomeService.scheduleCallback(companyId, targetId, callbackAt);
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
    public List<CampaignTarget> claimDueTargetsByScore(long campaignId, int limit, int localHour) {
        return campaignTargetRepository.claimDueByScore(campaignId, limit, localHour);
    }

    @Override
    public List<CampaignTarget> findTargetsToScore(long campaignId, Instant scoredBefore, int limit) {
        return campaignTargetRepository.findToScore(campaignId, scoredBefore, limit);
    }

    @Override
    public void updateTargetScores(long companyId, Map<Long, TargetScore> scores) {
        if (!scores.isEmpty()) {
            campaignTargetRepository.updateScores(companyId, scores);
        }
    }

    @Override
    public CampaignTarget claimNextForOperator(long companyId, long campaignId, long userId) {
        requireCampaignExists(companyId, campaignId);
        return campaignTargetRepository.claimNextForOperator(campaignId, userId);
    }

    @Override
    public CampaignTarget findHeldByOperator(long companyId, long campaignId, long userId) {
        requireCampaignExists(companyId, campaignId);
        return campaignTargetRepository.findHeldByOperator(campaignId, userId);
    }

    @Override
    public void settleByOperator(long companyId, long targetId, TargetStatus status, Instant callbackAt) {
        campaignOutcomeService.settleByOperator(companyId, targetId, status, callbackAt);
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
    public Map<Long, Long> findCompanyIdsOfExpiredClaims(Instant claimedBefore) {
        return campaignTargetRepository.findCompanyIdsOfExpiredClaims(claimedBefore);
    }

    /** The campaign is this company's — every target write below is keyed by campaign id alone. */
    private void requireCampaignExists(long companyId, long campaignId) {
        if (campaignRepository.find(companyId, campaignId) == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
        }
    }
}
