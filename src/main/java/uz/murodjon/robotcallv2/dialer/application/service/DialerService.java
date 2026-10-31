package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsWarmup;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.service.ContactFrequencyPolicy;
import uz.murodjon.robotcallv2.dialer.domain.service.DialWindow;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.phonenumber.application.port.input.PhoneNumberUseCase;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumber;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.CallerIdPoolNumber;

import java.time.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The scheduled dialer (PROJECT.md §5.2, §10). Each tick it scans ACTIVE campaigns,
 * and for those inside their dial window dispatches due targets to RabbitMQ —
 * bounded by {@code dispatch-batch} (rate) and free concurrency slots
 * ({@code max-concurrent-calls}). A separate sweep reclaims calls that were
 * dispatched but never answered, freeing their slot and scheduling a retry.
 */
@Service
public class DialerService {

    private static final Logger log = LoggerFactory.getLogger(DialerService.class);

    /** How long a target waits after the company's balance could not cover its call. */
    private static final Duration NO_BALANCE_RETRY = Duration.ofMinutes(15);
    /** How long a target waits when its campaign's caller ID pool has no number it can be dialled from. */
    private static final Duration NO_CALLER_ID_RETRY = Duration.ofMinutes(5);
    /** How long past its lease an IN_PROGRESS target may sit before {@link #reclaimExpiredClaims} takes it back. */
    private static final Duration CLAIM_GRACE = Duration.ofMinutes(30);
    /** How often a company is told its balance is holding calls back. */
    private static final Duration BALANCE_NOTICE_INTERVAL = Duration.ofHours(1);
    private static final Duration CAP_UNREADABLE_WARN_INTERVAL = Duration.ofMinutes(5);

    private final DialerProperties dialerProperties;
    private final RtpProperties rtpProperties;
    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final SipTrunkUseCase sipTrunkUseCase;
    private final RabbitTemplate rabbitTemplate;
    private final DialerState state;
    private final OutboundCallRegistry registry;
    private final AriService ariService;
    private final GracefulShutdownManager shutdown;
    private final TtsWarmup ttsWarmup;
    private final Clock clock;
    private final CampaignVariantUseCase campaignVariantUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final CallBillingUseCase callBilling;
    /** Where the contact policy reads a number's history from. */
    private final CallAttemptUseCase callAttemptUseCase;
    private final TrunkHealth trunkHealth;
    private final ClusterJobLock clusterJobLock;
    private final CompanyConcurrencyService companyConcurrency;
    private final CallerIdPoolUseCase callerIdPoolUseCase;
    private final PhoneNumberUseCase phoneNumberUseCase;

    private final Map<Long, AtomicInteger> campaignTrunkCounters = new ConcurrentHashMap<>();
    /** Company id → when it was last told its balance is holding calls. */
    private final Map<Long, Instant> balanceNoticeAt = new ConcurrentHashMap<>();
    /** When each campaign's spend was last summed against its budget — one ledger query a minute, not a tick. */
    private final Map<Long, Instant> budgetCheckedAt = new ConcurrentHashMap<>();
    /** When each campaign last logged an unreadable daily-cap counter — once per interval, not per tick. */
    private final Map<Long, Instant> capUnreadableWarnedAt = new ConcurrentHashMap<>();

    public DialerService(DialerProperties dialerProperties, RtpProperties rtpProperties,
                         CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                         CompanyConfigUseCase companyConfigUseCase, BusinessScheduleUseCase businessScheduleUseCase,
                         SipTrunkUseCase sipTrunkUseCase,
                         RabbitTemplate rabbitTemplate, DialerState state,
                         OutboundCallRegistry registry, AriService ariService,
                         GracefulShutdownManager shutdown, TtsWarmup ttsWarmup, Clock clock,
                         CampaignVariantUseCase campaignVariantUseCase, AiAgentUseCase aiAgentUseCase,
                         CallBillingUseCase callBilling, CallAttemptUseCase callAttemptUseCase, TrunkHealth trunkHealth,
                         ClusterJobLock clusterJobLock,
                         CompanyConcurrencyService companyConcurrency,
                         CallerIdPoolUseCase callerIdPoolUseCase, PhoneNumberUseCase phoneNumberUseCase) {
        this.companyConcurrency = companyConcurrency;
        this.callerIdPoolUseCase = callerIdPoolUseCase;
        this.phoneNumberUseCase = phoneNumberUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.trunkHealth = trunkHealth;
        this.clusterJobLock = clusterJobLock;
        this.dialerProperties = dialerProperties;
        this.rtpProperties = rtpProperties;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.rabbitTemplate = rabbitTemplate;
        this.state = state;
        this.registry = registry;
        this.ariService = ariService;
        this.shutdown = shutdown;
        this.ttsWarmup = ttsWarmup;
        this.clock = clock;
        this.campaignVariantUseCase = campaignVariantUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.callBilling = callBilling;
    }

    /**
     * One instance dispatches at a time: the concurrency caps and the daily cap are
     * check-then-reserve, and two ticks reading the same counters at once each dialled up to
     * the limit. An instant call does not dispatch on its own thread — it adds a due target
     * that the next tick claims first (InstantCallTriggerService).
     */
    @Scheduled(fixedDelayString = "#{${voice-agent.dialer.tick-seconds:5} * 1000}")
    public void dispatch() {
        clusterJobLock.runExclusively("dialer.dispatch", this::dispatchDueTargets);
    }

    /**
     * Every instance, not only the one dispatching: the dispatcher sizes the platform's
     * ceiling from what the cluster announced ({@link DialerState#countClusterCapacity}).
     * A draining instance announces 0, so no new call is counted against its ports. Its
     * live targets go with it, for whichever instance runs {@link #reclaimExpiredClaims}.
     */
    @Scheduled(fixedDelay = 10_000)
    public void announceToCluster() {
        state.announceCapacity(shutdown.isDraining() ? 0 : rtpProperties.mediaCapacity());
        state.announceLiveTargets(registry.liveTargetIds());
    }

    private void dispatchDueTargets() {
        if (!dialerProperties.enabled() || shutdown.isDraining()) {
            return; // no new calls while shutting down
        }
        // Operator campaigns are dialled for their queue's operators, not by this loop — and
        // left out before the priority split, so they take no share of the AI batch.
        List<Campaign> active = campaignUseCase.findActiveCampaigns().stream()
                .filter(campaign -> campaign.dialing().mode().placesAiCalls())
                .toList();
        if (active.isEmpty()) {
            return;
        }
        // The tick's batch is split by priority, not equally: a priority-5 campaign gets
        // five lines for every one a priority-1 campaign gets. Every campaign keeps at
        // least one so a low priority is slow, never starved.
        int totalPriority = active.stream().mapToInt(Campaign::priority).sum();
        // Every live instance's ports, read once per tick: it moves with instances, not with this sweep.
        int clusterCapacity = state.countClusterCapacity(rtpProperties.mediaCapacity());

        for (Campaign campaign : active) {
            int perCampaign = Math.max(1, dialerProperties.dispatchBatch() * campaign.priority() / totalPriority);
            // The company's date, not the server's: a daily cap that rolled over at the
            // server's midnight gave a tenant in another zone two "days" in one of its own.
            LocalDate today = companyToday(campaign.companyId());
            if (!isWithinDialWindow(campaign)) {
                log.debug("Campaign {} ({}) skipped: outside dial window or allowed days", campaign.id(), campaign.name());
                continue;
            }
            if (overBudget(campaign)) {
                log.info("Campaign {} ({}) reached its budget — pausing", campaign.id(), campaign.name());
                campaignUseCase.pauseByPlatform(campaign.companyId(), campaign.id(), "byudjet tugadi (BUDGET_CAP)");
                continue;
            }
            // Everything about how this campaign's calls sound. Read once per tick rather
            // than once per target: a tick dials at most `dispatch-batch` numbers and they
            // all speak as the same agent.
            AiAgent agent;
            try {
                agent = aiAgentUseCase.requirePublishedAgent(campaign.companyId(), campaign.aiAgentId());
            } catch (RuntimeException e) {
                log.error("Campaign {} ({}) skipped: {}", campaign.id(), campaign.name(), e.getMessage());
                continue;
            }
            if (!agent.enabled()) {
                log.debug("Campaign {} skipped: agent {} is disabled", campaign.id(), agent.id());
                continue;
            }
            // Once per campaign per tick, before any target is claimed: claiming first
            // would mark a batch as taken for a company that cannot pay for a single call.
            if (!callBilling.hasBalanceForCall(campaign.companyId())) {
                log.info("Campaign {} ({}) skipped: company {} has no balance left",
                        campaign.id(), campaign.name(), campaign.companyId());
                continue;
            }

            // Pre-warm campaign phrases on-demand if not already done
            ttsWarmup.warmUpForCampaign(campaign, agent);

            // The platform's ceiling is physical: past the RTP port range a call is
            // answered and then dropped for want of a port, which costs the subscriber a
            // ring and us a connected minute. The active count is the cluster's, so the
            // ports it is measured against are every live instance's, not this one's.
            int freePlatform = Math.min(dialerProperties.maxConcurrentCalls(), clusterCapacity)
                    - state.countActiveTotal();
            if (freePlatform <= 0) {
                log.debug("Platform concurrency saturated (active={}/max={})",
                        state.countActiveTotal(), dialerProperties.maxConcurrentCalls());
                return; // nothing can be dialled this tick, by anyone
            }
            // The company's own share (its plan's limit, or the platform default). `continue`, not
            // `return`: one tenant filling its quota is not a reason for the next tenant in this
            // sweep to dial nothing.
            int freeCompany = companyConcurrency.countFree(campaign.companyId());
            if (freeCompany <= 0) {
                log.debug("Company {} concurrency saturated (active={})", campaign.companyId(),
                        state.countActive(campaign.companyId()));
                continue;
            }
            // The campaign's own ceiling (campaign.maxConcurrentCalls) — 0 means no ceiling
            // of its own, only the company's and the platform's.
            int freeCampaign = campaign.limits().maxConcurrentCalls() > 0
                    ? campaign.limits().maxConcurrentCalls() - state.countActiveForCampaign(campaign.id())
                    : Integer.MAX_VALUE;
            if (freeCampaign <= 0) {
                log.debug("Campaign {} concurrency saturated (active={}/max={})", campaign.id(),
                        state.countActiveForCampaign(campaign.id()), campaign.limits().maxConcurrentCalls());
                continue;
            }
            int free = Math.min(Math.min(freePlatform, freeCompany), freeCampaign);
            int batch = Math.min(free, perCampaign);
            batch = Math.min(batch, remainingToday(campaign, today));
            if (batch <= 0) {
                continue; // campaign has spent its day (§C14)
            }
            // Before the targets are claimed, not after: a campaign whose selected trunks
            // are all disabled has nothing to dial with, and claiming first would leave
            // the batch marked as taken with no call ever placed for it.
            // The line comes from the campaign's numbers, never from an agent: a pool or fixed-number
            // campaign dials each number over that number's own trunk, any other over every trunk.
            Long callerIdPoolId = campaign.numbers().callerIdPoolId();
            Long fromNumberId = campaign.numbers().fromNumberId();
            PhoneNumber fromNumber = null;
            List<SipTrunkRow> candidateTrunks;
            try {
                candidateTrunks = sipTrunkUseCase.findAllEnabledForCompany(campaign.companyId());
                if (fromNumberId != null) {
                    fromNumber = requireFromNumber(campaign, candidateTrunks);
                    long fromTrunkId = fromNumber.providerTrunkId();
                    candidateTrunks = candidateTrunks.stream().filter(trunk -> trunk.id() == fromTrunkId).toList();
                }
                if (candidateTrunks.isEmpty()) {
                    ariService.requirePlatformTrunkAllowed(campaign.companyId());
                }
            } catch (ConflictException e) {
                // Paused, not skipped. With no usable trunk the campaign cannot place a
                // single call, and leaving it ACTIVE shows an owner a running campaign
                // that silently dials nothing — while this loop repeats the same error
                // every few seconds. PAUSED states it once, and an operator restarts it
                // when the trunk is back, choosing then whether to dial at once.
                log.error("Campaign {} ({}) paused: {}", campaign.id(), campaign.name(), e.getMessage());
                // setStatus, not pause(): this thread serves every tenant's campaigns and
                // pause() scopes the update to the request's company, which here is the
                // platform default — another tenant's campaign would stay ACTIVE.
                campaignUseCase.pauseByPlatform(campaign.companyId(), campaign.id(), e.getMessage());
                continue;
            }
            // A trunk whose registration is down, or that has just failed several calls
            // in a row, is left out of the rotation while the others carry the load. If
            // every trunk is out, the list stays as it is: a call that fails is still an
            // attempt the owner can see, a call never placed is not.
            List<SipTrunkRow> healthy = candidateTrunks.stream().filter(trunkHealth::isUsable).toList();
            if (healthy.isEmpty() && !candidateTrunks.isEmpty()) {
                log.warn("Campaign {}: every SIP trunk is down or cooling off — dialling over all of them anyway",
                        campaign.id());
            } else {
                candidateTrunks = healthy;
            }
            // A trunk's own channel count (maxChannels, from the provider's contract): past it
            // the provider refuses the call, so dialling there spends an attempt on a refusal.
            Map<Long, Integer> freeByTrunk = new HashMap<>();
            for (SipTrunkRow trunk : candidateTrunks) {
                freeByTrunk.put(trunk.id(), trunk.maxChannels() != null
                        ? trunk.maxChannels() - state.countActiveForTrunk(trunk.id())
                        : Integer.MAX_VALUE);
            }
            if (!candidateTrunks.isEmpty()) {
                long freeOnTrunks = freeByTrunk.values().stream().mapToLong(channels -> Math.max(0, channels)).sum();
                if (freeOnTrunks <= 0) {
                    log.debug("Campaign {}: every SIP trunk is at its channel limit", campaign.id());
                    continue;
                }
                batch = (int) Math.min(batch, freeOnTrunks);
            }

            List<CampaignTarget> due = campaign.dialing().targetOrdering() == TargetOrdering.SCORE
                    ? campaignTargetUseCase.claimDueTargetsByScore(campaign.id(), batch, companyHour(campaign.companyId()))
                    : campaignTargetUseCase.claimDueTargets(campaign.id(), batch);
            if (due.isEmpty()) {
                log.debug("Campaign {} has no due targets to claim", campaign.id());
                continue;
            }

            AtomicInteger counter = campaignTrunkCounters.computeIfAbsent(campaign.id(), k -> new AtomicInteger(0));
            CompanyConfig contactPolicy = companyConfigUseCase.find(campaign.companyId());
            Map<Long, AiAgent> stepAgents = new HashMap<>();

            for (CampaignTarget t : due) {
                // Before the money and the slot: a number the company's contact policy
                // holds back is not dialled, and the claim is undone rather than spent —
                // nothing was placed, so nothing may count as an attempt.
                Instant heldUntil = heldByContactPolicy(contactPolicy, campaign.companyId(), t.phone());
                if (heldUntil != null) {
                    campaignTargetUseCase.releaseClaim(t.id(), heldUntil);
                    log.info("Target {} ({}) of campaign {} held until {} by the contact policy",
                            t.id(), PhoneMasker.mask(t.phone()), campaign.id(), heldUntil);
                    continue;
                }
                // Check recipient's local timezone quiet hours (08:00 - 20:00)
                Set<LocalDate> holidays = holidaysOf(campaign.companyId(), contactPolicy);
                if (!DialWindow.isOpenForRecipient(campaign, contactPolicy, holidays, t.phone(), clock)) {
                    campaignTargetUseCase.releaseClaim(t.id(), Instant.now(clock).plus(Duration.ofMinutes(30)));
                    log.info("Target {} ({}) of campaign {} held: outside recipient local dial window",
                            t.id(), PhoneMasker.mask(t.phone()), campaign.id());
                    continue;
                }
                // The agent of the step this target is on; the campaign's own when it has no sequence.
                // Left to a later tick, like a held number, when that agent cannot speak now.
                AiAgent stepAgent = findStepAgent(campaign, t, agent, stepAgents);
                if (stepAgent == null || !stepAgent.enabled()) {
                    campaignTargetUseCase.releaseClaim(t.id(), Instant.now(clock).plus(Duration.ofMinutes(30)));
                    log.warn("Target {} of campaign {} held: the agent of its sequence step is unavailable",
                            t.id(), campaign.id());
                    continue;
                }
                // Number and trunk are chosen together for a pool campaign: a pool number goes out
                // only on its own trunk, so it is picked among those whose trunk has a free channel.
                // With none the call waits, like a held number — it never goes out under the
                // trunk's or the platform's number instead.
                CallerIdPoolNumber callerIdNumber = null;
                if (callerIdPoolId != null) {
                    callerIdNumber = acquireCallerId(campaign, t, freeByTrunk);
                    if (callerIdNumber == null) {
                        campaignTargetUseCase.releaseClaim(t.id(), Instant.now(clock).plus(NO_CALLER_ID_RETRY));
                        log.warn("Target {} of campaign {} held: caller ID pool {} has no number on a trunk with a free channel",
                                t.id(), campaign.id(), callerIdPoolId);
                        continue;
                    }
                }
                // The money before the slot: a hold that cannot be taken means this number
                // is not dialled at all, and holding a concurrency slot for it would keep
                // the next tenant's call out of a line nobody is using.
                if (!callBilling.reserveForCall(campaign.companyId(), t.id())) {
                    // Claimed a moment ago by claimDue; give the claim back — including the
                    // attempt it counted, because nothing was dialled — and try again in a
                    // while rather than on the very next tick. Every other target this tick
                    // claimed for the campaign would fail the same hold, so they go back too.
                    Instant retryAt = Instant.now(clock).plus(NO_BALANCE_RETRY);
                    campaignTargetUseCase.releaseClaim(t.id(), retryAt);
                    for (CampaignTarget rest : due.subList(due.indexOf(t) + 1, due.size())) {
                        campaignTargetUseCase.releaseClaim(rest.id(), retryAt);
                    }
                    log.warn("Company {} has no balance for campaign {} — {} claimed target(s) released until {}",
                            campaign.companyId(), campaign.id(), due.size() - due.indexOf(t), retryAt);
                    // Told once an hour, not once a tick: the owner needs one notification,
                    // not seven hundred.
                    Instant lastNotice = balanceNoticeAt.get(campaign.companyId());
                    if (lastNotice == null || lastNotice.plus(BALANCE_NOTICE_INTERVAL).isBefore(Instant.now(clock))) {
                        balanceNoticeAt.put(campaign.companyId(), Instant.now(clock));
                        campaignUseCase.notifyBalanceExhausted(campaign.companyId(), campaign.id());
                    }
                    break;
                }
                state.reserve(campaign.companyId(), campaign.id());
                state.countDispatch(campaign.id(), today);
                String language = t.language() != null ? t.language() : stepAgent.language();

                // Never null while there are trunks: the batch was capped at their free channels.
                Long selectedTrunkId = callerIdNumber != null
                        ? takeChannel(callerIdNumber.trunkId(), freeByTrunk)
                        : selectTrunk(candidateTrunks, counter, freeByTrunk);
                state.reserveTrunk(selectedTrunkId);

                // Assigned here rather than when the task is consumed: this is the one place
                // that knows the campaign is dialling, so it is also where the attempt is
                // counted against the variant. Keyed on the number, so a retry of this same
                // target lands on the same script and the two attempts do not end up
                // credited to different variants.
                CampaignVariant variant =
                        campaignVariantUseCase.findForCall(campaign.companyId(), campaign.id(), t.phone());
                Long variantId = null;
                long aiAgentId = stepAgent.id();
                String ttsVoiceOverride = null;
                String promptOverride = null;
                PipelineMode pipelineOverride = null;
                if (variant != null) {
                    variantId = variant.id();
                    // A variant that names its own agent is testing a different voice,
                    // persona or script wholesale; one that only names a voice is testing
                    // that voice against the agent it otherwise shares.
                    aiAgentId = variant.aiAgentId() != null ? variant.aiAgentId() : aiAgentId;
                    ttsVoiceOverride = variant.ttsVoiceId();
                    promptOverride = variant.promptOverride();
                    pipelineOverride = variant.pipelineMode();
                    campaignVariantUseCase.recordCall(variantId);
                }

                rabbitTemplate.convertAndSend(CallTask.QUEUE,
                        new CallTask(campaign.id(), t.id(), t.clientId(), t.phone(), language,
                                t.contextData(), campaign.companyId(), aiAgentId, selectedTrunkId,
                                variantId, ttsVoiceOverride, promptOverride, pipelineOverride,
                                callerIdPoolId, callerIdNumber != null ? callerIdNumber.phone()
                                        : fromNumber != null ? fromNumber.phoneNumber() : null));
                log.info("Dispatched target {} ({}) of campaign {} via trunk {}{}", t.id(),
                        PhoneMasker.mask(t.phone()), campaign.id(),
                        selectedTrunkId, variantId != null ? ", variant " + variant.name() : "");
            }
        }
    }

    /**
     * The agent that speaks on this target's next call: the one named by the sequence step it is
     * on, else the campaign's own. Null when that agent cannot be read as a published one.
     * TTS warm-up and the enabled check stay the campaign agent's.
     */
    private AiAgent findStepAgent(Campaign campaign, CampaignTarget target, AiAgent campaignAgent,
                                  Map<Long, AiAgent> cache) {
        CampaignSequence sequence = campaign.sequence();
        if (sequence == null) {
            return campaignAgent;
        }
        SequenceStep step = target.stepId() != null ? sequence.findStep(target.stepId()).orElse(null) : null;
        if (step == null) {
            step = sequence.findStep(sequence.startStepId()).orElse(null);
        }
        Long stepAgentId = step != null && step.conversation() != null ? step.conversation().aiAgentId() : null;
        if (stepAgentId == null || stepAgentId.equals(campaignAgent.id())) {
            return campaignAgent;
        }
        try {
            return cache.computeIfAbsent(stepAgentId,
                    id -> aiAgentUseCase.requirePublishedAgent(campaign.companyId(), id));
        } catch (RuntimeException e) {
            log.error("Campaign {}: agent {} of sequence step {} is not usable: {}", campaign.id(), stepAgentId,
                    step.id(), e.getMessage());
            return null;
        }
    }

    /**
     * The campaign's fixed number, which must still be the company's and sit on one of its enabled
     * trunks ({@code companyTrunks}). Otherwise the campaign cannot place a call as it was set up,
     * and it is paused like a campaign with no trunk — never dialled under another number.
     */
    private PhoneNumber requireFromNumber(Campaign campaign, List<SipTrunkRow> companyTrunks) {
        long fromNumberId = campaign.numbers().fromNumberId();
        PhoneNumber number = phoneNumberUseCase.findCompanyNumber(campaign.companyId(), fromNumberId);
        if (number == null || !number.hasOwnTrunk()
                || companyTrunks.stream().noneMatch(trunk -> trunk.id() == number.providerTrunkId())) {
            throw new ConflictException(ErrorCode.CAMPAIGN_FROM_NUMBER_UNUSABLE,
                    number != null ? number.phoneNumber() : fromNumberId);
        }
        return number;
    }

    /**
     * The campaign pool's next number whose trunk still has a free channel this tick — the one
     * closest to the callee's when the campaign asks for local presence. Null when there is none,
     * or the pool cannot be read (the target then waits, as for none).
     */
    private CallerIdPoolNumber acquireCallerId(Campaign campaign, CampaignTarget target, Map<Long, Integer> freeByTrunk) {
        Set<Long> trunksWithFreeChannel = new HashSet<>();
        freeByTrunk.forEach((trunkId, free) -> {
            if (free > 0) {
                trunksWithFreeChannel.add(trunkId);
            }
        });
        try {
            return callerIdPoolUseCase.acquireCallerIdOnTrunks(campaign.companyId(),
                    campaign.numbers().callerIdPoolId(),
                    campaign.numbers().localPresence() ? target.phone() : null, trunksWithFreeChannel);
        } catch (RuntimeException e) {
            log.error("Campaign {}: caller ID pool {} could not be read: {}", campaign.id(),
                    campaign.numbers().callerIdPoolId(), e.getMessage());
            return null;
        }
    }

    /** One of {@code trunkId}'s free channels, which the caller ID pool only offered because it had one. */
    private static Long takeChannel(long trunkId, Map<Long, Integer> freeByTrunk) {
        freeByTrunk.merge(trunkId, -1, Integer::sum);
        return trunkId;
    }

    /**
     * The next trunk in rotation that still has a free channel, taking one of them; null when
     * there are no trunks (the platform's own carries the call) or every one is full.
     */
    private static Long selectTrunk(List<SipTrunkRow> trunks, AtomicInteger counter, Map<Long, Integer> freeByTrunk) {
        for (int tried = 0; tried < trunks.size(); tried++) {
            SipTrunkRow trunk = trunks.get(Math.abs(counter.getAndIncrement() % trunks.size()));
            int free = freeByTrunk.get(trunk.id());
            if (free > 0) {
                freeByTrunk.put(trunk.id(), free - 1);
                return trunk.id();
            }
        }
        return null;
    }

    /**
     * When the company's contact policy lets this number be called, or null for now — also
     * null when the company has no policy switched on, in which case no history is read.
     */
    private Instant heldByContactPolicy(CompanyConfig config, long companyId, String phone) {
        if (config == null || !config.limitsContactFrequency()) {
            return null;
        }
        Instant now = Instant.now(clock);
        ContactHistory history = callAttemptUseCase.findContactHistory(companyId, phone,
                now.minus(ContactFrequencyPolicy.WINDOW), config.maxAttemptsPerPhonePerWeek());
        return ContactFrequencyPolicy.nextAllowed(config.maxAttemptsPerPhonePerWeek(),
                config.quietDaysAfterConversation(), config.respectPromisedDate(), history, now,
                zoneOf(config.timezone()));
    }

    private ZoneId zoneOf(String timezone) {
        try {
            return timezone != null && !timezone.isBlank() ? ZoneId.of(timezone) : clock.getZone();
        } catch (DateTimeException e) {
            return clock.getZone();
        }
    }

    /** "Today" for the daily cap: the company's date, in its own zone. */
    private LocalDate companyToday(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return LocalDate.now(config != null ? clock.withZone(config.zoneId()) : clock);
    }

    /** The hour a SCORE campaign's best-hour bonus is matched against: the company's, in its own zone. */
    private int companyHour(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return LocalTime.now(config != null ? clock.withZone(config.zoneId()) : clock).getHour();
    }

    private Set<LocalDate> holidaysOf(long companyId, CompanyConfig config) {
        if (businessScheduleUseCase == null) {
            return Set.of();
        }
        ZoneId zone = config != null ? config.zoneId() : clock.getZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return businessScheduleUseCase.findHolidayDates(companyId, today, today);
    }

    private int remainingToday(Campaign campaign, LocalDate today) {
        int cap = campaign.limits().dailyCallCap();
        if (cap <= 0) {
            return Integer.MAX_VALUE;
        }
        OptionalInt dispatched = state.countDispatchedToday(campaign.id(), today);
        if (dispatched.isEmpty()) {
            // Fail closed: without today's count the cap cannot be kept, and reading it as
            // zero (as this used to) let a Redis outage dial a capped campaign without limit.
            Instant now = Instant.now(clock);
            Instant warned = capUnreadableWarnedAt.get(campaign.id());
            if (warned == null || warned.plus(CAP_UNREADABLE_WARN_INTERVAL).isBefore(now)) {
                capUnreadableWarnedAt.put(campaign.id(), now);
                log.warn("Campaign {} not dialled: its daily cap ({}) cannot be checked — the counter is unreadable",
                        campaign.id(), cap);
            }
            return 0;
        }
        int used = dispatched.getAsInt();
        int remaining = cap - used;
        if (remaining <= 0) {
            log.info("Campaign {} reached its daily cap ({} calls) — pausing until tomorrow",
                    campaign.id(), cap);
        }
        return remaining;
    }

    /**
     * Takes back targets a crash or restart left IN_PROGRESS. claimDue stamps each claim
     * with next_attempt_at = now + 5 min as a lease, but nothing ever read it: after a
     * restart those targets were never dialled again, their campaign never completed — so
     * a recurring one never re-ran — and their money stayed held. A target whose call any
     * instance still tracks is left alone, since a long conversation outlives the lease.
     * Settled as FAILED, so the campaign's own retry policy decides whether it is dialled
     * again. The lock keeps two instances from settling the same target twice; the live set
     * is the cluster's ({@link DialerState#findClusterLiveTargetIds}), and when it cannot be
     * read nothing is settled this round.
     */
    @Scheduled(fixedDelay = 60_000)
    public void reclaimExpiredClaims() {
        clusterJobLock.runExclusively("dialer.reclaim-expired-claims", this::settleExpiredClaims);
    }

    private void settleExpiredClaims() {
        Set<Long> live = state.findClusterLiveTargetIds().orElse(null);
        if (live == null) {
            log.warn("Expired claims not settled this round: the cluster's live calls are unknown");
            return;
        }
        // This instance's own straight from memory, so a call placed since its last
        // announcement is not missed.
        live.addAll(registry.liveTargetIds());
        campaignTargetUseCase.findCompanyIdsOfExpiredClaims(Instant.now(clock).minus(CLAIM_GRACE)).forEach((targetId, companyId) -> {
            if (live.contains(targetId)) {
                return;
            }
            callBilling.releaseReservation(companyId, targetId);
            campaignTargetUseCase.applyOutcome(companyId, targetId, Disposition.FAILED);
            log.warn("Reclaimed target {} of company {} — still IN_PROGRESS {} after its claim ran out",
                    targetId, companyId, CLAIM_GRACE);
        });
    }

    @Scheduled(fixedDelay = 30_000)
    public void reclaimStale() {
        for (String channelId : registry.staleUnanswered(Duration.ofSeconds(dialerProperties.reclaimAfterSec()))) {
            OutboundCall oc = registry.remove(channelId);
            if (oc != null) {
                state.release(oc.companyId(), oc.campaignId());
                state.releaseTrunk(oc.sipTrunkId());
                callBilling.releaseReservation(oc.companyId(), oc.targetId());
                campaignTargetUseCase.applyOutcome(oc.companyId(), oc.targetId(), Disposition.NO_ANSWER);
                ariService.hangupChannel(channelId);
                log.info("Reclaimed unanswered call {} (target {})", channelId, oc.targetId());
            }
        }
    }

    /**
     * Whether the campaign's settled spend has reached its total or daily budget (C.5). The
     * ledger is summed at most once a minute per campaign; a call already in flight when
     * the line is crossed still settles, so the overshoot is at most one tick's batch.
     */
    private boolean overBudget(Campaign campaign) {
        if (campaign.limits().budgetUzs() == null && campaign.limits().dailyBudgetUzs() == null) {
            return false;
        }
        Instant now = Instant.now(clock);
        Instant checked = budgetCheckedAt.get(campaign.id());
        if (checked != null && checked.plusSeconds(60).isAfter(now)) {
            return false;
        }
        budgetCheckedAt.put(campaign.id(), now);
        if (campaign.limits().budgetUzs() != null
                && callBilling.sumSpentUzsByCampaign(campaign.companyId(), campaign.id(), null) >= campaign.limits().budgetUzs()) {
            return true;
        }
        if (campaign.limits().dailyBudgetUzs() != null) {
            CompanyConfig config = companyConfigUseCase.find(campaign.companyId());
            ZoneId zone = config != null ? config.zoneId() : clock.getZone();
            Instant dayStart = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
            return callBilling.sumSpentUzsByCampaign(campaign.companyId(), campaign.id(), dayStart)
                    >= campaign.limits().dailyBudgetUzs();
        }
        return false;
    }

    /** Also read by CallTaskConsumer, which re-checks it just before dialling a queued task. */
    boolean isWithinDialWindow(Campaign campaign) {
        CompanyConfig config = companyConfigUseCase.find(campaign.companyId());
        Set<LocalDate> holidays = holidaysOf(campaign.companyId(), config);
        return DialWindow.isOpen(campaign, config, holidays, clock);
    }

    /** Re-checks dial window considering both company configuration and recipient's local timezone. */
    boolean isWithinDialWindowForRecipient(Campaign campaign, String recipientPhone) {
        CompanyConfig config = companyConfigUseCase.find(campaign.companyId());
        Set<LocalDate> holidays = holidaysOf(campaign.companyId(), config);
        return DialWindow.isOpenForRecipient(campaign, config, holidays, recipientPhone, clock);
    }
}
