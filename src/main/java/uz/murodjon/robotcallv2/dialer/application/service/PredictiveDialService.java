package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.queue.PredictiveDialer;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetOrdering;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.application.dto.CallTask;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.domain.service.ContactFrequencyPolicy;
import uz.murodjon.robotcallv2.dialer.domain.service.DialPacingCalculator;
import uz.murodjon.robotcallv2.dialer.domain.service.DialWindow;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * PREDICTIVE campaigns (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.4): each tick, as many clients
 * are dialled as {@link DialPacingCalculator} says the free operators of the campaign's queue
 * can take — more than one each while the answer rate is low and the AI agent is not taking
 * too many of the answered clients.
 *
 * <p>Every number placed is an AI call in waiting, so these calls hold the dialer's
 * concurrency slots and count against the platform's, the company's and the campaign's
 * ceilings, as the AI dialer's do. The rest of what an AI call is checked against — window,
 * balance, a published and enabled agent, the do-not-call list, the contact policy — is
 * checked here too. The budget and the daily cap are not, as for PROGRESSIVE.
 *
 * <p>What happens once a client is dialled — finding an operator, the answering-machine watch,
 * handing over to the AI — is {@link PredictiveDialer}'s.
 */
@Service
public class PredictiveDialService {

    private static final Logger log = LoggerFactory.getLogger(PredictiveDialService.class);

    private final DialerProperties dialerProperties;
    private final RtpProperties rtpProperties;
    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final DoNotCallUseCase doNotCallUseCase;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final OutboundCallPreparer outboundCallPreparer;
    private final DialerState state;
    private final AriService ariService;
    private final PredictiveDialer predictiveDialer;
    private final GracefulShutdownManager shutdown;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;
    private final CompanyConcurrencyService companyConcurrency;

    public PredictiveDialService(DialerProperties dialerProperties, RtpProperties rtpProperties,
                                 CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                                 CallQueueUseCase callQueueUseCase, CompanyConfigUseCase companyConfigUseCase,
                                 CallAttemptUseCase callAttemptUseCase, CallBillingUseCase callBillingUseCase,
                                 AiAgentUseCase aiAgentUseCase, DoNotCallUseCase doNotCallUseCase,
                                 OperatorPresenceUseCase operatorPresenceUseCase,
                                 OperatorAccountUseCase operatorAccountUseCase,
                                 OutboundCallPreparer outboundCallPreparer, DialerState state, AriService ariService,
                                 PredictiveDialer predictiveDialer, GracefulShutdownManager shutdown,
                                 ClusterJobLock clusterJobLock, Clock clock,
                                 CompanyConcurrencyService companyConcurrency) {
        this.companyConcurrency = companyConcurrency;
        this.dialerProperties = dialerProperties;
        this.rtpProperties = rtpProperties;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.doNotCallUseCase = doNotCallUseCase;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.outboundCallPreparer = outboundCallPreparer;
        this.state = state;
        this.ariService = ariService;
        this.predictiveDialer = predictiveDialer;
        this.shutdown = shutdown;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    /** The dialer's own tick; one instance at a time, as the AI dialer. */
    @Scheduled(fixedDelayString = "#{${voice-agent.dialer.tick-seconds:5} * 1000}")
    public void dispatch() {
        clusterJobLock.runExclusively("dialer.predictive", this::dialAhead);
    }

    void dialAhead() {
        if (!dialerProperties.enabled() || shutdown.isDraining()) {
            return;
        }
        for (Campaign campaign : campaignUseCase.findActiveCampaigns()) {
            if (campaign.dialMode() != DialMode.PREDICTIVE || campaign.callQueueId() == null
                    || campaign.aiAgentId() == null) {
                continue;
            }
            try {
                dialForCampaign(campaign);
            } catch (Exception e) {
                log.warn("Predictive campaign {} not dialled this tick: {}", campaign.id(), e.getMessage());
            }
        }
    }

    private void dialForCampaign(Campaign campaign) {
        long companyId = campaign.companyId();
        CompanyConfig config = companyConfigUseCase.find(companyId);
        if (!DialWindow.isOpen(campaign, config, clock) || !callBillingUseCase.hasBalanceForCall(companyId)) {
            return;
        }
        AiAgent agent = aiAgentUseCase.requirePublishedAgent(companyId, campaign.aiAgentId());
        if (!agent.enabled()) {
            return; // nobody to take the overflow: not a call is placed ahead of the operators
        }
        CallQueue queue = callQueueUseCase.findQueue(companyId, campaign.callQueueId());
        if (queue == null) {
            return;
        }
        int calls = DialPacingCalculator.countCallsToPlace(countFreeOperators(companyId, queue),
                predictiveDialer.countUndecided(campaign.id()), predictiveDialer.findStats(campaign.id()),
                campaign.maxAiOverflowPercent());
        calls = Math.min(calls, countFreeSlots(campaign));
        if (calls <= 0) {
            return;
        }
        List<CampaignTarget> due = campaign.targetOrdering() == TargetOrdering.SCORE
                ? campaignTargetUseCase.claimDueTargetsByScore(campaign.id(), calls,
                        LocalTime.now(clock.withZone(config.zoneId())).getHour())
                : campaignTargetUseCase.claimDueTargets(campaign.id(), calls);
        for (CampaignTarget target : due) {
            dialTarget(campaign, config, agent, target);
        }
    }

    /** AVAILABLE, with a softphone signed in to take the call on. */
    private int countFreeOperators(long companyId, CallQueue queue) {
        int free = 0;
        for (CallQueueMember member : queue.members()) {
            if (operatorPresenceUseCase.find(companyId, member.userId()).status() != OperatorStatus.AVAILABLE) {
                continue;
            }
            OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, member.userId());
            if (account != null && ariService.isEndpointOnline(account.endpointName())) {
                free++;
            }
        }
        return free;
    }

    /** The least of the platform's, the company's and the campaign's free concurrency slots. */
    private int countFreeSlots(Campaign campaign) {
        int freePlatform = Math.min(dialerProperties.maxConcurrentCalls(),
                state.countClusterCapacity(rtpProperties.mediaCapacity())) - state.countActiveTotal();
        int freeCompany = companyConcurrency.countFree(campaign.companyId());
        int freeCampaign = campaign.maxConcurrentCalls() > 0
                ? campaign.maxConcurrentCalls() - state.countActiveForCampaign(campaign.id())
                : Integer.MAX_VALUE;
        return Math.min(Math.min(freePlatform, freeCompany), freeCampaign);
    }

    private void dialTarget(Campaign campaign, CompanyConfig config, AiAgent agent, CampaignTarget target) {
        long companyId = campaign.companyId();
        if (doNotCallUseCase.contains(companyId, target.phone())) {
            callAttemptUseCase.recordUnplacedAttempt(companyId, target.id(), target.phone(), target.language(),
                    Disposition.DO_NOT_CALL, "number is in the do-not-call list");
            campaignTargetUseCase.applyOutcome(companyId, target.id(), Disposition.DO_NOT_CALL);
            return;
        }
        Instant heldUntil = findHeldByContactPolicy(config, companyId, target.phone());
        if (heldUntil != null) {
            campaignTargetUseCase.releaseClaim(target.id(), heldUntil);
            log.info("Target {} ({}) of campaign {} held until {} by the contact policy", target.id(),
                    PhoneMasker.mask(target.phone()), campaign.id(), heldUntil);
            return;
        }
        state.reserve(companyId, campaign.id());
        try {
            String language = target.language() != null ? target.language() : agent.language();
            OutboundCall aiCall = outboundCallPreparer.prepare(new CallTask(campaign.id(), target.id(),
                    target.clientId(), target.phone(), language, target.contextData(), companyId, agent.id(),
                    null, null, null, null, null));
            predictiveDialer.dial(campaign.callQueueId(), aiCall);
            log.info("Predictive: target {} ({}) of campaign {} dialled", target.id(),
                    PhoneMasker.mask(target.phone()), campaign.id());
        } catch (Exception e) {
            // The attempt the claim counted stands: the call was tried and failed.
            log.warn("Predictive: target {} of campaign {} could not be dialled: {}", target.id(), campaign.id(),
                    e.getMessage());
            state.release(companyId, campaign.id());
            campaignTargetUseCase.applyOutcome(companyId, target.id(), Disposition.FAILED);
        }
    }

    /** When the company's contact policy lets this number be called, or null for now. */
    private Instant findHeldByContactPolicy(CompanyConfig config, long companyId, String phone) {
        if (config == null || !config.limitsContactFrequency()) {
            return null;
        }
        Instant now = clock.instant();
        ContactHistory history = callAttemptUseCase.findContactHistory(companyId, phone,
                now.minus(ContactFrequencyPolicy.WINDOW), config.maxAttemptsPerPhonePerWeek());
        return ContactFrequencyPolicy.nextAllowed(config.maxAttemptsPerPhonePerWeek(),
                config.quietDaysAfterConversation(), config.respectPromisedDate(), history, now, config.zoneId());
    }
}
