package uz.murodjon.robotcallv2.campaign.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.dto.*;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CallQueueLookupPort;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.TargetSourceRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignAutoRetries;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignNumbers;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignKind;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignStateMachine;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.campaign.domain.service.SequenceValidator;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.siptrunk.application.dto.CallerIdPoolDetail;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Campaigns themselves: created, edited and cloned, listed with their target counts, and
 * moved through their lifecycle — started, scheduled, paused, completed, run again on
 * their recurrence. Targets are {@link CampaignTargetService}'s; what a finished call does
 * to its target is {@link CampaignOutcomeService}'s.
 */
@Service
public class CampaignService implements CampaignUseCase {

    private static final Logger log = LoggerFactory.getLogger(CampaignService.class);

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final TargetSourceRepository targetSourceRepository;
    private final AiAgentUseCase aiAgentUseCase;
    private final UserUseCase userUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final AuditUseCase auditUseCase;
    private final NotificationUseCase notificationUseCase;
    private final TtsWarmup ttsWarmup;
    private final CampaignTargetService campaignTargetService;
    /** Takes a STANDARD campaign's filtered contacts at launch. */
    private final AutoEnrollService autoEnrollService;
    private final CampaignOutcomeService campaignOutcomeService;
    private final CampaignValidator campaignValidator;
    private final SequenceValidator sequenceValidator;
    private final CallerIdPoolUseCase callerIdPoolUseCase;
    private final PhoneNumberUseCase phoneNumberUseCase;
    private final CallQueueLookupPort callQueueLookupPort;
    private final Clock clock;

    public CampaignService(CampaignRepository campaignRepository, CampaignTargetRepository campaignTargetRepository,
                           TargetSourceRepository targetSourceRepository, AiAgentUseCase aiAgentUseCase,
                           UserUseCase userUseCase, CompanyConfigUseCase companyConfigUseCase,
                           AuditUseCase auditUseCase, NotificationUseCase notificationUseCase, TtsWarmup ttsWarmup,
                           CampaignTargetService campaignTargetService, CampaignOutcomeService campaignOutcomeService,
                           CampaignValidator campaignValidator, SequenceValidator sequenceValidator,
                           CallerIdPoolUseCase callerIdPoolUseCase, PhoneNumberUseCase phoneNumberUseCase,
                           CallQueueLookupPort callQueueLookupPort, AutoEnrollService autoEnrollService, Clock clock) {
        this.phoneNumberUseCase = phoneNumberUseCase;
        this.autoEnrollService = autoEnrollService;
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.targetSourceRepository = targetSourceRepository;
        this.aiAgentUseCase = aiAgentUseCase;
        this.userUseCase = userUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.auditUseCase = auditUseCase;
        this.notificationUseCase = notificationUseCase;
        this.ttsWarmup = ttsWarmup;
        this.campaignTargetService = campaignTargetService;
        this.campaignOutcomeService = campaignOutcomeService;
        this.campaignValidator = campaignValidator;
        this.sequenceValidator = sequenceValidator;
        this.callerIdPoolUseCase = callerIdPoolUseCase;
        this.callQueueLookupPort = callQueueLookupPort;
        this.clock = clock;
    }

    private static final Set<DayOfWeek> DEFAULT_DIAL_DAYS = EnumSet.allOf(DayOfWeek.class);
    private static final LocalTime DRAFT_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime DRAFT_WINDOW_END = LocalTime.of(18, 0);
    /** A group the request left out: every field empty, read exactly like the omitted fields it stands for. */
    private static final CampaignScheduleDto NO_SCHEDULE = new CampaignScheduleDto(null, null, null, null, null);
    private static final CampaignRetryDto NO_RETRY = new CampaignRetryDto(0, 0, null);
    private static final CampaignLimitsDto NO_LIMITS = new CampaignLimitsDto(0, 0, null, null, null);
    private static final CampaignRecurrenceDto NO_RECURRENCE = new CampaignRecurrenceDto(null, null, null, null);
    private static final CampaignDialingDto NO_DIALING = new CampaignDialingDto(null, null, null, null);

    @Override
    public CreateCampaignResponse createCampaign(long companyId, CreateCampaignRequest request) {
        CampaignScheduleDto schedule = request.schedule() != null ? request.schedule() : NO_SCHEDULE;
        CampaignRetryDto retry = request.retry() != null ? request.retry() : NO_RETRY;
        CampaignLimitsDto limits = request.limits() != null ? request.limits() : NO_LIMITS;
        CampaignRecurrenceDto recurrence = request.recurrence() != null ? request.recurrence() : NO_RECURRENCE;
        CampaignDialingDto dialing = request.dialing() != null ? request.dialing() : NO_DIALING;
        DialMode dialMode = dialing.mode() != null ? dialing.mode() : DialMode.AI;
        // An empty AI draft (Retell's "Untitled campaign") starts with the default sequence. A request that
        // names an agent but no sequence (batch call, older clients) keeps the pre-sequence dialing path.
        boolean emptyDraft = request.sequence() == null && request.aiAgentId() == null && dialMode == DialMode.AI;
        CampaignSequence sequence = emptyDraft ? CampaignSequence.createDefault() : request.sequence();
        AiAgent agent = requireDialers(companyId, dialMode, resolveAiAgentId(request.aiAgentId(), sequence),
                dialing.callQueueId(), true);
        int dailyCallCap = Math.max(0, limits.dailyCallCap());
        CompanyConfig companyConfig = companyConfigUseCase.find(companyId);
        LocalTime companyStart = companyConfig != null ? companyConfig.dialWindowStart() : null;
        LocalTime companyEnd = companyConfig != null ? companyConfig.dialWindowEnd() : null;
        // Retell's default calling window, 09:00–18:00 every day, kept inside the company's own hours.
        LocalTime draftStart = emptyDraft ? latest(DRAFT_WINDOW_START, companyStart) : companyStart;
        LocalTime draftEnd = emptyDraft ? earliest(DRAFT_WINDOW_END, companyEnd) : companyEnd;
        if (draftStart != null && draftEnd != null && !draftStart.isBefore(draftEnd)) {
            draftStart = companyStart;
            draftEnd = companyEnd;
        }
        LocalTime dialWindowStart = resolveWindowBound(schedule.dialWindowStart(), draftStart, LocalTime.of(9, 0));
        LocalTime dialWindowEnd = resolveWindowBound(schedule.dialWindowEnd(), draftEnd, LocalTime.of(20, 0));
        requireWindowWithinCompany(companyId, dialWindowStart, dialWindowEnd);
        campaignValidator.validateRetryPolicy(retry.policyOrNone());
        requireSequence(companyId, sequence);
        requireNumbers(companyId, request.numbers());
        AutoEnrollment autoEnroll = request.autoEnroll() != null ? request.autoEnroll() : AutoEnrollment.NONE;
        campaignValidator.validateAutoEnroll(request.kind(), autoEnroll);
        Campaign row = Campaign.builder()
                .name(request.name())
                .status(CampaignStatus.DRAFT)
                .aiAgentId(agent != null ? agent.id() : null)
                .companyId(companyId)
                .kind(request.kind())
                .autoEnroll(autoEnroll)
                .sequence(sequence)
                // Retell turns both on for a new campaign; anything else asks for them explicitly.
                .autoRetries(request.autoRetries() != null ? request.autoRetries()
                        : emptyDraft ? CampaignAutoRetries.DEFAULT : CampaignAutoRetries.NONE)
                .numbers(request.numbers())
                .priority(request.priorityOrDefault())
                .schedule(new CampaignSchedule(schedule.startAt(), schedule.endAt(), dialWindowStart, dialWindowEnd,
                        schedule.dialDays() != null && !schedule.dialDays().isEmpty()
                                ? schedule.dialDays() : DEFAULT_DIAL_DAYS))
                .retry(new CampaignRetry(retry.maxAttempts() > 0 ? retry.maxAttempts() : 3,
                        Math.max(0, retry.intervalMinutes()), retry.policyOrNone()))
                .limits(new CampaignLimits(limits.maxConcurrentCalls() > 0 ? limits.maxConcurrentCalls() : 20,
                        dailyCallCap, limits.budgetUzs(), limits.dailyBudgetUzs(), limits.maxCps()))
                .recurrence(new CampaignRecurrence(recurrence.typeOrDefault(), recurrence.dayOfMonth(),
                        recurrence.cronExpression(), recurrence.autoResetTargetsOrDefault()))
                .dialing(new CampaignDialing(dialMode, dialing.callQueueId(),
                        dialing.maxAiOverflowPercent() != null ? dialing.maxAiOverflowPercent()
                                : Campaign.DEFAULT_MAX_AI_OVERFLOW_PERCENT,
                        dialing.targetOrdering()))
                .build();
        requireSchedule(schedule.startAt(), schedule.endAt());
        long id = campaignRepository.create(companyId, row);
        auditUseCase.record(companyId, "CAMPAIGN_CREATE", "campaign", String.valueOf(id),
                request.name() + " (" + dialMode + ", agent " + (agent != null ? agent.name() : "-")
                        + ", queue " + (dialing.callQueueId() != null ? dialing.callQueueId() : "-")
                        + ", cap/day=" + dailyCallCap
                        + ", recurrence=" + recurrence.typeOrDefault() + ")");
        return new CreateCampaignResponse(id, CampaignStatus.DRAFT);
    }

    @Override
    public CampaignRow updateCampaign(long companyId, long id, UpdateCampaignRequest request) {
        CampaignScheduleDto schedule = request.schedule() != null ? request.schedule() : NO_SCHEDULE;
        CampaignRetryDto retry = request.retry() != null ? request.retry() : NO_RETRY;
        CampaignLimitsDto limits = request.limits() != null ? request.limits() : NO_LIMITS;
        CampaignRecurrenceDto recurrence = request.recurrence() != null ? request.recurrence() : NO_RECURRENCE;
        CampaignDialingDto dialing = request.dialing() != null ? request.dialing() : NO_DIALING;
        Campaign existing = requireCampaign(companyId, id);
        // Not sent: the sequence the campaign has, so a client that predates sequences edits as before.
        CampaignSequence sequence = request.sequence() != null ? request.sequence() : existing.sequence();
        AiAgent agent = requireDialers(existing.companyId(), existing.dialing().mode(),
                resolveAiAgentId(request.aiAgentId(), sequence), dialing.callQueueId(),
                existing.status() == CampaignStatus.DRAFT);
        // An omitted window keeps the one the campaign has.
        LocalTime dialWindowStart = resolveWindowBound(schedule.dialWindowStart(),
                existing.schedule().dialWindowStart(), null);
        LocalTime dialWindowEnd = resolveWindowBound(schedule.dialWindowEnd(), existing.schedule().dialWindowEnd(), null);
        requireWindowWithinCompany(existing.companyId(), dialWindowStart, dialWindowEnd);
        campaignValidator.validateRetryPolicy(retry.policyOrNone());
        requireSequence(companyId, sequence);
        // Not sent: the numbers the campaign has, as with the sequence.
        CampaignNumbers numbers = request.numbers() != null ? request.numbers() : existing.numbers();
        requireNumbers(companyId, numbers);
        // Not sent: the rules the campaign has, as with the sequence.
        AutoEnrollment autoEnroll = request.autoEnroll() != null ? request.autoEnroll() : existing.autoEnroll();
        // As in Retell, the conditions lock at launch: who a running campaign enrols does not change under it.
        if (existing.status() != CampaignStatus.DRAFT && !autoEnroll.equals(existing.autoEnroll())) {
            throw new ConflictException(ErrorCode.CAMPAIGN_AUTO_ENROLL_LOCKED, id);
        }
        campaignValidator.validateAutoEnroll(existing.kind(), autoEnroll);
        // A STANDARD draft takes its contacts from a filter or from a file, never both (Retell).
        if (existing.kind() == CampaignKind.STANDARD && existing.status() == CampaignStatus.DRAFT
                && existing.autoEnroll().hasNoRules() && !autoEnroll.hasNoRules()) {
            long uploaded = campaignTargetRepository.countByCampaign(companyId, id);
            if (uploaded > 0) {
                throw new ConflictException(ErrorCode.CAMPAIGN_CONTACTS_UPLOADED, id, uploaded);
            }
        }
        // The kind, status, company, creator, last run and dial mode stay the existing campaign's.
        Campaign row = existing.toBuilder()
                .name(request.name())
                .aiAgentId(agent != null ? agent.id() : null)
                .sequence(sequence)
                .numbers(numbers)
                .autoEnroll(autoEnroll)
                // Not sent: what the campaign has, as with the sequence.
                .autoRetries(request.autoRetries() != null ? request.autoRetries() : existing.autoRetries())
                .priority(request.priorityOrDefault())
                .schedule(new CampaignSchedule(schedule.startAt(), schedule.endAt(), dialWindowStart, dialWindowEnd,
                        schedule.dialDays()))
                .retry(new CampaignRetry(retry.maxAttempts(), Math.max(0, retry.intervalMinutes()),
                        retry.policyOrNone()))
                .limits(new CampaignLimits(limits.maxConcurrentCalls(), Math.max(0, limits.dailyCallCap()),
                        limits.budgetUzs(), limits.dailyBudgetUzs(), limits.maxCps()))
                .recurrence(new CampaignRecurrence(recurrence.typeOrDefault(), recurrence.dayOfMonth(),
                        recurrence.cronExpression(), recurrence.autoResetTargetsOrDefault()))
                .dialing(new CampaignDialing(existing.dialing().mode(), dialing.callQueueId(),
                        dialing.maxAiOverflowPercent() != null ? dialing.maxAiOverflowPercent()
                                : existing.dialing().maxAiOverflowPercent(),
                        dialing.targetOrdering() != null ? dialing.targetOrdering()
                                : existing.dialing().targetOrdering()))
                // Not sent: the version just read, so a client that predates it edits as before.
                .version(request.version() != null ? request.version() : existing.version())
                .build();
        requireSchedule(schedule.startAt(), schedule.endAt());
        if (!campaignRepository.update(companyId, id, row)) {
            throw new ConflictException(ErrorCode.ENTITY_VERSION_CONFLICT, "campaign", id);
        }
        auditUseCase.record(companyId, "CAMPAIGN_UPDATE", "campaign", String.valueOf(id), request.name());
        return requireCampaignRow(companyId, id);
    }

    @Override
    public CampaignRow clone(long companyId, long id) {
        Campaign source = requireCampaign(companyId, id);
        Campaign row = source.toBuilder()
                .id(0)
                .name(source.name() + " (nusxa)")
                .status(CampaignStatus.DRAFT)
                .createdBy(null)
                .lastRunAt(null)
                .consentConfirmedAt(null)
                .version(0L)
                .build();
        long newId = campaignRepository.create(companyId, row);
        auditUseCase.record(companyId, "CAMPAIGN_CLONE", "campaign", String.valueOf(newId), "from " + id);
        return requireCampaignRow(companyId, newId);
    }

    /**
     * The agent a campaign is dialled under: the one named, else the first one its sequence
     * calls with — until the dialer reads the sequence itself, this keeps {@code aiAgentId}
     * pointing at who speaks.
     */
    private static Long resolveAiAgentId(Long requested, CampaignSequence sequence) {
        if (requested != null || sequence == null) {
            return requested;
        }
        return sequence.steps().stream()
                .filter(step -> step.type() == SequenceStepType.CONVERSATION && step.conversation() != null)
                .map(step -> step.conversation().aiAgentId())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * The pool must be the company's own, and local presence has to have a pool to choose from. A
     * fixed number is either the pool's alternative or nothing: it must be the company's, on a trunk
     * of the company's own, and never set together with a pool.
     */
    private void requireNumbers(long companyId, CampaignNumbers numbers) {
        if (numbers == null) {
            return;
        }
        if (numbers.localPresence() && numbers.callerIdPoolId() == null) {
            throw new ValidationException(ErrorCode.CAMPAIGN_LOCAL_PRESENCE_NEEDS_POOL);
        }
        if (numbers.fromNumberId() != null && numbers.callerIdPoolId() != null) {
            throw new ValidationException(ErrorCode.CAMPAIGN_FROM_NUMBER_WITH_POOL);
        }
        if (numbers.callerIdPoolId() != null) {
            callerIdPoolUseCase.getPool(companyId, numbers.callerIdPoolId());
        }
        if (numbers.fromNumberId() != null) {
            PhoneNumber fromNumber = phoneNumberUseCase.findCompanyNumber(companyId, numbers.fromNumberId());
            if (fromNumber == null) {
                throw new NotFoundException(ErrorCode.PHONE_NUMBER_NOT_FOUND, numbers.fromNumberId());
            }
            if (!fromNumber.hasOwnTrunk()) {
                throw new ConflictException(ErrorCode.CAMPAIGN_FROM_NUMBER_UNUSABLE, fromNumber.phoneNumber());
            }
        }
    }

    /** A sequence must be well formed and call only with the company's own agents. */
    private void requireSequence(long companyId, CampaignSequence sequence) {
        sequenceValidator.validate(sequence);
        if (sequence != null) {
            sequence.steps().stream()
                    .filter(step -> step.type() == SequenceStepType.CONVERSATION && step.conversation() != null)
                    .map(step -> step.conversation().aiAgentId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(agentId -> aiAgentUseCase.requireAgent(companyId, agentId));
        }
    }

    /**
     * Checks that the campaign names who dials for its mode, and that what it names is the
     * company's own — a queue id from another tenant would hand its clients to strangers.
     *
     * @param draft a DRAFT may be saved with no agent yet; it is asked for when the campaign is launched
     * @return the named agent, or null when the campaign has none
     */
    private AiAgent requireDialers(long companyId, DialMode dialMode, Long aiAgentId, Long callQueueId,
                                   boolean draft) {
        if (!draft) {
            campaignValidator.validateAiAgent(dialMode, aiAgentId);
        }
        campaignValidator.validateCallQueue(dialMode, callQueueId);
        if (callQueueId != null && !callQueueLookupPort.existsByCompanyIdAndId(companyId, callQueueId)) {
            throw new NotFoundException(ErrorCode.CALL_QUEUE_NOT_FOUND, callQueueId);
        }
        return aiAgentId != null ? aiAgentUseCase.requireAgent(companyId, aiAgentId) : null;
    }

    /**
     * One end of a campaign's dial window: the one asked for, else the fallback — on create the
     * company's own dial window, so a campaign that names no hours calls in the company's.
     */
    private static LocalTime latest(LocalTime a, LocalTime b) {
        return b == null || a.isAfter(b) ? a : b;
    }

    private static LocalTime earliest(LocalTime a, LocalTime b) {
        return b == null || a.isBefore(b) ? a : b;
    }

    private static LocalTime resolveWindowBound(LocalTime requested, LocalTime fallback, LocalTime lastResort) {
        if (requested != null) {
            return requested;
        }
        return fallback != null ? fallback : lastResort;
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
                rows.stream().map(Campaign::aiAgentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> creatorNames = userUseCase.findNamesByIds(companyId,
                rows.stream().map(Campaign::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(c -> CampaignRow.of(c, c.aiAgentId() != null ? agentNames.get(c.aiAgentId()) : null,
                        c.createdBy() != null ? creatorNames.get(c.createdBy()) : null,
                        statsMap.getOrDefault(c.id(), CampaignTargetStats.ZERO)))
                .toList();
    }

    @Override
    public CampaignRow requireCampaignRow(long companyId, long id) {
        Campaign c = requireCampaign(companyId, id);
        String agentName = c.aiAgentId() != null
                ? aiAgentUseCase.findNamesByIds(List.of(c.aiAgentId())).get(c.aiAgentId())
                : null;
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

    @Override
    public List<Campaign> findActiveCampaigns() {
        return campaignRepository.findActive();
    }

    @Override
    public List<Campaign> findCampaignsByName(long companyId, String query, int limit) {
        return campaignRepository.searchByName(companyId, query, limit);
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
        requireLaunchable(companyId, c);
        Instant now = clock.instant();
        // The first launch asks for the legal consent once; a campaign that was launched before, or
        // already has it, is not asked again when it is resumed, restarted or its scheduled time comes.
        if (c.status() == CampaignStatus.DRAFT && c.consentConfirmedAt() == null) {
            throw new ValidationException(ErrorCode.CAMPAIGN_CONSENT_REQUIRED, campaignId);
        }
        // Retell's snapshot: a STANDARD campaign's filter is taken once, at launch — a scheduled one too, not at its
        // start time — and later matches do not join.
        if (c.status() == CampaignStatus.DRAFT && c.kind() == CampaignKind.STANDARD && !c.autoEnroll().hasNoRules()) {
            autoEnrollService.snapshotFilteredContacts(c);
        }
        // A future startAt parks the campaign; "immediate" overrides it — the operator is
        // standing at the button and wants it now, whatever the plan said.
        if (!immediate && c.schedule().startAt() != null && c.schedule().startAt().isAfter(now)) {
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

    /**
     * Whether the campaign could place its calls if it were started now.
     *
     * @throws AppException the first thing that stops it
     */
    private void requireLaunchable(long companyId, Campaign c) {
        campaignValidator.validateAiAgent(c.dialing().mode(), c.aiAgentId());
        sequenceValidator.validateRunnable(c.sequence());
        // Every dialer tick skips a campaign whose agent is disabled, so it would show
        // ACTIVE and never ring anyone. A sequence calls with the agent of each step.
        Set<Long> agentIds = new LinkedHashSet<>();
        if (c.aiAgentId() != null) {
            agentIds.add(c.aiAgentId());
        }
        if (c.sequence() != null) {
            c.sequence().steps().stream()
                    .filter(step -> step.type() == SequenceStepType.CONVERSATION)
                    .map(step -> step.conversation().aiAgentId())
                    .forEach(agentIds::add);
        }
        for (Long agentId : agentIds) {
            if (!aiAgentUseCase.requireAgent(companyId, agentId).enabled()) {
                throw new ConflictException(ErrorCode.AI_AGENT_DISABLED, agentId);
            }
        }
        // A campaign with nothing to dial and no source that will fill it — a target source, the
        // rules an auto-enroll campaign adds contacts by, or a STANDARD draft's filter that matches
        // someone now — is not started, it is shown ACTIVE and never rings anyone.
        boolean filled = c.kind() == CampaignKind.AUTO_ENROLL
                ? !c.autoEnroll().hasNoRules()
                : c.status() == CampaignStatus.DRAFT && !c.autoEnroll().hasNoRules()
                        && autoEnrollService.countFilteredContacts(c) > 0;
        if (campaignTargetRepository.countByCampaign(companyId, c.id()) == 0
                && targetSourceRepository.findByCampaignId(c.id()) == null && !filled) {
            throw new ConflictException(ErrorCode.CAMPAIGN_HAS_NO_TARGETS, c.id());
        }
        if (c.schedule().endAt() != null && !c.schedule().endAt().isAfter(clock.instant())) {
            throw new ConflictException(ErrorCode.CAMPAIGN_END_PASSED, c.id(), c.schedule().endAt());
        }
    }

    @Override
    public CampaignRow acceptConsent(long companyId, long campaignId) {
        Campaign c = requireCampaign(companyId, campaignId);
        if (c.consentConfirmedAt() == null) {
            campaignRepository.recordConsent(companyId, campaignId, clock.instant());
            auditUseCase.record(companyId, "CAMPAIGN_CONSENT_CONFIRMED", "campaign", String.valueOf(campaignId), c.name());
        }
        return requireCampaignRow(companyId, campaignId);
    }

    @Override
    public CampaignOverviewResponse requireCampaignOverview(long companyId, long campaignId) {
        Campaign c = requireCampaign(companyId, campaignId);
        Long poolId = c.numbers().callerIdPoolId();
        CallerIdPoolDetail pool = poolId != null ? callerIdPoolUseCase.getPool(companyId, poolId) : null;
        LaunchBlocker blocker = null;
        try {
            requireLaunchable(companyId, c);
        } catch (AppException e) {
            blocker = new LaunchBlocker(e.code().name(), e.getMessage());
        }
        return new CampaignOverviewResponse(requireCampaignRow(companyId, campaignId),
                pool != null ? pool.name() : null, pool != null ? pool.numbers().size() : 0, blocker);
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
                    campaignOutcomeService.checkCompletion(c.companyId(), c.id());
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
        campaignTargetService.syncFromSourceQuietly(companyId, campaignId);
        campaignRepository.recordRecurrenceRun(campaignId, Instant.now(clock), CampaignStatus.ACTIVE);
        ttsWarmup.warmUpForCampaign(campaign);
        log.info("Triggered recurring run for campaign {}", campaignId);
    }
}
