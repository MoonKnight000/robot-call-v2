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
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignFilter;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignStateMachine;
import uz.murodjon.robotcallv2.campaign.domain.service.CampaignValidator;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
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
    private final CampaignOutcomeService campaignOutcomeService;
    private final CampaignValidator campaignValidator;
    private final CallQueueLookupPort callQueueLookupPort;
    private final Clock clock;

    public CampaignService(CampaignRepository campaignRepository, CampaignTargetRepository campaignTargetRepository,
                           TargetSourceRepository targetSourceRepository, AiAgentUseCase aiAgentUseCase,
                           UserUseCase userUseCase, CompanyConfigUseCase companyConfigUseCase,
                           AuditUseCase auditUseCase, NotificationUseCase notificationUseCase, TtsWarmup ttsWarmup,
                           CampaignTargetService campaignTargetService, CampaignOutcomeService campaignOutcomeService,
                           CampaignValidator campaignValidator, CallQueueLookupPort callQueueLookupPort, Clock clock) {
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
        this.callQueueLookupPort = callQueueLookupPort;
        this.clock = clock;
    }

    private static final Set<DayOfWeek> DEFAULT_DIAL_DAYS = EnumSet.allOf(DayOfWeek.class);
    /** A group the request left out: every field empty, read exactly like the omitted fields it stands for. */
    private static final CampaignScheduleDto NO_SCHEDULE = new CampaignScheduleDto(null, null, null, null, null);
    private static final CampaignRetryDto NO_RETRY = new CampaignRetryDto(0, 0, null);
    private static final CampaignLimitsDto NO_LIMITS = new CampaignLimitsDto(0, 0, null, null);
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
        AiAgent agent = requireDialers(companyId, dialMode, request.aiAgentId(), dialing.callQueueId());
        int dailyCallCap = Math.max(0, limits.dailyCallCap());
        CompanyConfig companyConfig = companyConfigUseCase.find(companyId);
        LocalTime dialWindowStart = resolveWindowBound(schedule.dialWindowStart(),
                companyConfig != null ? companyConfig.dialWindowStart() : null, LocalTime.of(9, 0));
        LocalTime dialWindowEnd = resolveWindowBound(schedule.dialWindowEnd(),
                companyConfig != null ? companyConfig.dialWindowEnd() : null, LocalTime.of(20, 0));
        requireWindowWithinCompany(companyId, dialWindowStart, dialWindowEnd);
        campaignValidator.validateRetryPolicy(retry.policyOrNone());
        Campaign row = Campaign.builder()
                .name(request.name())
                .type(request.type())
                .status(CampaignStatus.DRAFT)
                .aiAgentId(agent != null ? agent.id() : null)
                .companyId(companyId)
                .priority(request.priorityOrDefault())
                .schedule(new CampaignSchedule(schedule.startAt(), schedule.endAt(), dialWindowStart, dialWindowEnd,
                        schedule.dialDays() != null && !schedule.dialDays().isEmpty()
                                ? schedule.dialDays() : DEFAULT_DIAL_DAYS))
                .retry(new CampaignRetry(retry.maxAttempts() > 0 ? retry.maxAttempts() : 3,
                        Math.max(0, retry.intervalMinutes()), retry.policyOrNone()))
                .limits(new CampaignLimits(limits.maxConcurrentCalls() > 0 ? limits.maxConcurrentCalls() : 20,
                        dailyCallCap, limits.budgetUzs(), limits.dailyBudgetUzs()))
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
        AiAgent agent = requireDialers(existing.companyId(), existing.dialing().mode(), request.aiAgentId(),
                dialing.callQueueId());
        // An omitted window keeps the one the campaign has.
        LocalTime dialWindowStart = resolveWindowBound(schedule.dialWindowStart(),
                existing.schedule().dialWindowStart(), null);
        LocalTime dialWindowEnd = resolveWindowBound(schedule.dialWindowEnd(), existing.schedule().dialWindowEnd(), null);
        requireWindowWithinCompany(existing.companyId(), dialWindowStart, dialWindowEnd);
        campaignValidator.validateRetryPolicy(retry.policyOrNone());
        // The type, status, company, creator, last run and dial mode stay the existing campaign's.
        Campaign row = existing.toBuilder()
                .name(request.name())
                .aiAgentId(agent != null ? agent.id() : null)
                .priority(request.priorityOrDefault())
                .schedule(new CampaignSchedule(schedule.startAt(), schedule.endAt(), dialWindowStart, dialWindowEnd,
                        schedule.dialDays()))
                .retry(new CampaignRetry(retry.maxAttempts(), Math.max(0, retry.intervalMinutes()),
                        retry.policyOrNone()))
                .limits(new CampaignLimits(limits.maxConcurrentCalls(), Math.max(0, limits.dailyCallCap()),
                        limits.budgetUzs(), limits.dailyBudgetUzs()))
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
                .version(0L)
                .build();
        long newId = campaignRepository.create(companyId, row);
        auditUseCase.record(companyId, "CAMPAIGN_CLONE", "campaign", String.valueOf(newId), "from " + id);
        return requireCampaignRow(companyId, newId);
    }

    /**
     * Checks that the campaign names who dials for its mode, and that what it names is the
     * company's own — a queue id from another tenant would hand its clients to strangers.
     *
     * @return the named agent, or null when the campaign has none
     */
    private AiAgent requireDialers(long companyId, DialMode dialMode, Long aiAgentId, Long callQueueId) {
        campaignValidator.validateDialMode(dialMode, aiAgentId, callQueueId);
        if (callQueueId != null && !callQueueLookupPort.existsByCompanyIdAndId(companyId, callQueueId)) {
            throw new NotFoundException(ErrorCode.CALL_QUEUE_NOT_FOUND, callQueueId);
        }
        return aiAgentId != null ? aiAgentUseCase.requireAgent(companyId, aiAgentId) : null;
    }

    /**
     * One end of a campaign's dial window: the one asked for, else the fallback — on create the
     * company's own dial window, so a campaign that names no hours calls in the company's.
     */
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
        // Every dialer tick skips a campaign whose agent is disabled, so it would show
        // ACTIVE and never ring anyone.
        if (c.aiAgentId() != null && !aiAgentUseCase.requireAgent(companyId, c.aiAgentId()).enabled()) {
            throw new ConflictException(ErrorCode.AI_AGENT_DISABLED, c.aiAgentId());
        }
        // A campaign with nothing to dial and no source that will fill it is not started,
        // it is shown ACTIVE and never rings anyone.
        if (campaignTargetRepository.countByCampaign(companyId, campaignId) == 0 && targetSourceRepository.findByCampaignId(campaignId) == null) {
            throw new ConflictException(ErrorCode.CAMPAIGN_HAS_NO_TARGETS, campaignId);
        }
        Instant now = clock.instant();
        if (c.schedule().endAt() != null && !c.schedule().endAt().isAfter(now)) {
            throw new ConflictException(ErrorCode.CAMPAIGN_END_PASSED, campaignId, c.schedule().endAt());
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
