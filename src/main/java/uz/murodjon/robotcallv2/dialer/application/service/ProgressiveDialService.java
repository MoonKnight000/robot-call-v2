package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.lifecycle.GracefulShutdownManager;
import uz.murodjon.robotcallv2.agent.queue.ProgressiveDialer;
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
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.domain.service.ContactFrequencyPolicy;
import uz.murodjon.robotcallv2.dialer.domain.service.DialWindow;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorAccountUseCase;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorPresenceUseCase;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Clock;
import java.time.Instant;

/**
 * PROGRESSIVE campaigns (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.3): each tick, every free
 * operator of the campaign's queue gets one client dialled for them — never more calls than
 * operators to take them.
 *
 * <p>An operator counts as free when AVAILABLE with a softphone signed in; they are claimed
 * (RINGING) before the target, so neither the queue nor another campaign can take them while
 * their client rings. The checks a preview card passes before it is handed out are made here
 * too: the campaign is ACTIVE, the dial window is open, the company has balance, and the
 * number is neither on the do-not-call list (the claim skips those) nor held by the contact
 * policy. Concurrency, the daily cap and the budget are not — the operators are the pace.
 *
 * <p>What happens once the client is dialled — answering-machine watch, ringing the
 * operator, settling the target — is {@link ProgressiveDialer}'s.
 */
@Service
public class ProgressiveDialService {

    private static final Logger log = LoggerFactory.getLogger(ProgressiveDialService.class);

    /** What the operator's presence shows as the call being rung for them. */
    private static final String RINGING_CHANNEL = "progressive";
    /** Numbers held by the contact policy are given back and the next one tried, this many times per operator. */
    private static final int MAX_CLAIMS_PER_OPERATOR = 5;

    private final DialerProperties dialerProperties;
    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final OperatorPresenceUseCase operatorPresenceUseCase;
    private final OperatorAccountUseCase operatorAccountUseCase;
    private final AriService ariService;
    private final ProgressiveDialer progressiveDialer;
    private final GracefulShutdownManager shutdown;
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;

    public ProgressiveDialService(DialerProperties dialerProperties, CampaignUseCase campaignUseCase,
                                  CampaignTargetUseCase campaignTargetUseCase, CallQueueUseCase callQueueUseCase,
                                  CompanyConfigUseCase companyConfigUseCase, CallAttemptUseCase callAttemptUseCase,
                                  CallBillingUseCase callBillingUseCase, OperatorPresenceUseCase operatorPresenceUseCase,
                                  OperatorAccountUseCase operatorAccountUseCase, AriService ariService,
                                  ProgressiveDialer progressiveDialer, GracefulShutdownManager shutdown,
                                  ClusterJobLock clusterJobLock, Clock clock) {
        this.dialerProperties = dialerProperties;
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.operatorPresenceUseCase = operatorPresenceUseCase;
        this.operatorAccountUseCase = operatorAccountUseCase;
        this.ariService = ariService;
        this.progressiveDialer = progressiveDialer;
        this.shutdown = shutdown;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
    }

    /** The dialer's own tick; one instance at a time, as the AI dialer. */
    @Scheduled(fixedDelayString = "#{${voice-agent.dialer.tick-seconds:5} * 1000}")
    public void dispatch() {
        clusterJobLock.runExclusively("dialer.progressive", this::dialForFreeOperators);
    }

    void dialForFreeOperators() {
        if (!dialerProperties.enabled() || shutdown.isDraining()) {
            return;
        }
        for (Campaign campaign : campaignUseCase.findActiveCampaigns()) {
            if (campaign.dialing().mode() != DialMode.PROGRESSIVE || campaign.dialing().callQueueId() == null) {
                continue;
            }
            try {
                dialForCampaign(campaign);
            } catch (Exception e) {
                log.warn("Progressive campaign {} not dialled this tick: {}", campaign.id(), e.getMessage());
            }
        }
    }

    private void dialForCampaign(Campaign campaign) {
        long companyId = campaign.companyId();
        CompanyConfig config = companyConfigUseCase.find(companyId);
        if (!DialWindow.isOpen(campaign, config, clock) || !callBillingUseCase.hasBalanceForCall(companyId)) {
            return;
        }
        CallQueue queue = callQueueUseCase.findQueue(companyId, campaign.dialing().callQueueId());
        if (queue == null) {
            return;
        }
        for (CallQueueMember member : queue.members()) {
            if (!isFree(companyId, member.userId())
                    || !operatorPresenceUseCase.claimForRinging(member.userId(), RINGING_CHANNEL)) {
                continue;
            }
            CampaignTarget target = claimTarget(config, campaign, member.userId());
            if (target == null) {
                operatorPresenceUseCase.releaseRinging(member.userId());
                return; // nothing due: the rest of the queue would draw nothing either
            }
            dial(campaign, target, member.userId());
        }
    }

    /** AVAILABLE, with a softphone signed in to take the call on. */
    private boolean isFree(long companyId, long userId) {
        if (operatorPresenceUseCase.find(companyId, userId).status() != OperatorStatus.AVAILABLE) {
            return false;
        }
        OperatorSipAccount account = operatorAccountUseCase.findSipAccount(companyId, userId);
        return account != null && ariService.isEndpointOnline(account.endpointName());
    }

    /** The next due number for this operator that the contact policy lets be called, or null. */
    private CampaignTarget claimTarget(CompanyConfig config, Campaign campaign, long userId) {
        for (int i = 0; i < MAX_CLAIMS_PER_OPERATOR; i++) {
            CampaignTarget claimed = campaignTargetUseCase.claimNextForOperator(campaign.companyId(), campaign.id(),
                    userId);
            if (claimed == null) {
                return null;
            }
            Instant heldUntil = findHeldByContactPolicy(config, campaign.companyId(), claimed.phone());
            if (heldUntil == null) {
                return claimed;
            }
            campaignTargetUseCase.releaseClaim(claimed.id(), heldUntil);
            log.info("Target {} ({}) of campaign {} held until {} by the contact policy", claimed.id(),
                    PhoneMasker.mask(claimed.phone()), campaign.id(), heldUntil);
        }
        return null;
    }

    private void dial(Campaign campaign, CampaignTarget target, long userId) {
        long companyId = campaign.companyId();
        String language = target.language() != null ? target.language()
                : Language.DEFAULT.code();
        try {
            progressiveDialer.dial(companyId, userId, target.id(), target.phone(), language);
            log.info("Progressive: target {} ({}) of campaign {} dialled for operator {}", target.id(),
                    PhoneMasker.mask(target.phone()), campaign.id(), userId);
        } catch (Exception e) {
            // The attempt the claim counted stands: the call was tried and failed.
            log.warn("Progressive: target {} of campaign {} could not be dialled: {}", target.id(), campaign.id(),
                    e.getMessage());
            operatorPresenceUseCase.releaseRinging(userId);
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
