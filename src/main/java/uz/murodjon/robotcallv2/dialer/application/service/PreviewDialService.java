package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.queue.OperatorFirstDialer;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewCardResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewDialResponse;
import uz.murodjon.robotcallv2.dialer.application.port.input.PreviewDialUseCase;
import uz.murodjon.robotcallv2.dialer.domain.service.ContactFrequencyPolicy;
import uz.murodjon.robotcallv2.dialer.domain.service.DialWindow;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * PREVIEW campaigns (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.2). An operator of the
 * campaign's queue takes one card at a time; the card is claimed like a dialer target —
 * IN_PROGRESS, one attempt counted — and stamped with who holds it.
 *
 * <p>What the AI dialer checks before a call is checked before a card is handed out: the
 * campaign is ACTIVE, the dial window is open, the company has balance, and the number is
 * neither on the do-not-call list (the claim skips those) nor held by the contact policy.
 * Concurrency and the daily cap are not: the operators themselves are the pace.
 *
 * <p>A dialled card is settled by its call: a client who never answers by
 * {@link OperatorFirstDialer}, one who does by the operator's wrap-up.
 */
@Service
public class PreviewDialService implements PreviewDialUseCase {

    private static final Logger log = LoggerFactory.getLogger(PreviewDialService.class);

    /** Numbers held by the contact policy are given back and the next one tried, this many times per request. */
    private static final int MAX_CLAIMS_PER_REQUEST = 5;
    /** A skipped card is due again after this — not at once, or the same operator would draw it next. */
    private static final Duration SKIP_DELAY = Duration.ofMinutes(15);

    private final CampaignUseCase campaignUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final OperatorFirstDialer operatorFirstDialer;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PreviewDialService(CampaignUseCase campaignUseCase, CampaignTargetUseCase campaignTargetUseCase,
                              CallQueueUseCase callQueueUseCase, CompanyConfigUseCase companyConfigUseCase,
                              CallAttemptUseCase callAttemptUseCase, CallBillingUseCase callBillingUseCase,
                              ClientMemoryUseCase clientMemoryUseCase, OperatorFirstDialer operatorFirstDialer,
                              CurrentUser currentUser, Clock clock) {
        this.campaignUseCase = campaignUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.operatorFirstDialer = operatorFirstDialer;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * The card already held comes back as it is — even from a paused campaign, so that a
     * dialled one can still be wrapped up and an undialled one skipped.
     */
    @Override
    public PreviewCardResponse findNextCard(long companyId, long campaignId) {
        long userId = requireUserId();
        Campaign campaign = requirePreviewCampaign(companyId, campaignId, userId);
        CampaignTarget held = campaignTargetUseCase.findHeldByOperator(companyId, campaignId, userId);
        if (held != null) {
            return toCard(companyId, campaign, held);
        }
        CompanyConfig config = companyConfigUseCase.find(companyId);
        requireDialling(campaign, config);
        for (int i = 0; i < MAX_CLAIMS_PER_REQUEST; i++) {
            CampaignTarget claimed = campaignTargetUseCase.claimNextForOperator(companyId, campaignId, userId);
            if (claimed == null) {
                return null;
            }
            // Given back rather than handed out, its attempt not counted: nothing is dialled.
            Instant heldUntil = findHeldByContactPolicy(config, companyId, claimed.phone());
            if (heldUntil == null) {
                return toCard(companyId, campaign, claimed);
            }
            campaignTargetUseCase.releaseClaim(claimed.id(), heldUntil);
            log.info("Target {} ({}) of campaign {} held until {} by the contact policy", claimed.id(),
                    PhoneMasker.mask(claimed.phone()), campaignId, heldUntil);
        }
        return null;
    }

    @Override
    public PreviewDialResponse callTarget(long companyId, long campaignId, long targetId) {
        long userId = requireUserId();
        Campaign campaign = requirePreviewCampaign(companyId, campaignId, userId);
        CampaignTarget target = requireHeld(companyId, campaignId, targetId, userId);
        requireDialling(campaign, companyConfigUseCase.find(companyId));
        String operatorChannelId = operatorFirstDialer.dialTarget(companyId, userId, target.phone(), target.id());
        return new PreviewDialResponse(target.id(), operatorChannelId);
    }

    @Override
    public void releaseTarget(long companyId, long campaignId, long targetId) {
        long userId = requireUserId();
        requirePreviewCampaign(companyId, campaignId, userId);
        CampaignTarget target = requireHeld(companyId, campaignId, targetId, userId);
        // A dialled card has had its attempt; giving the claim back would erase it.
        Long callId = findCallSinceClaim(companyId, target);
        if (callId != null) {
            throw new ConflictException(ErrorCode.PREVIEW_WRAP_UP_REQUIRED, targetId, callId);
        }
        campaignTargetUseCase.releaseClaim(targetId, clock.instant().plus(SKIP_DELAY));
    }

    /** The company's PREVIEW campaign, which the current user dials as a member of its queue. */
    private Campaign requirePreviewCampaign(long companyId, long campaignId, long userId) {
        Campaign campaign = campaignUseCase.requireCampaign(companyId, campaignId);
        if (campaign.dialMode() != DialMode.PREVIEW) {
            throw new ConflictException(ErrorCode.CAMPAIGN_NOT_PREVIEW, campaignId, campaign.dialMode());
        }
        if (campaign.callQueueId() == null) {
            throw new ConflictException(ErrorCode.CAMPAIGN_HAS_NO_QUEUE, campaignId);
        }
        CallQueue queue = callQueueUseCase.findQueue(companyId, campaign.callQueueId());
        if (queue == null) {
            throw new ConflictException(ErrorCode.CAMPAIGN_HAS_NO_QUEUE, campaignId);
        }
        if (queue.members().stream().noneMatch(member -> member.userId() == userId)) {
            throw new ForbiddenException(ErrorCode.PREVIEW_NOT_QUEUE_MEMBER, campaignId);
        }
        return campaign;
    }

    private void requireDialling(Campaign campaign, CompanyConfig config) {
        if (campaign.status() != CampaignStatus.ACTIVE) {
            throw new ConflictException(ErrorCode.CAMPAIGN_NOT_DIALLING, campaign.id(), campaign.status());
        }
        if (!DialWindow.isOpen(campaign, config, clock)) {
            throw new ConflictException(ErrorCode.CAMPAIGN_OUTSIDE_DIAL_WINDOW, campaign.id());
        }
        if (!callBillingUseCase.hasBalanceForCall(campaign.companyId())) {
            throw new ConflictException(ErrorCode.CALL_BALANCE_INSUFFICIENT);
        }
    }

    private CampaignTarget requireHeld(long companyId, long campaignId, long targetId, long userId) {
        CampaignTarget held = campaignTargetUseCase.findHeldByOperator(companyId, campaignId, userId);
        if (held == null || held.id() != targetId) {
            throw new ConflictException(ErrorCode.PREVIEW_TARGET_NOT_HELD, targetId);
        }
        return held;
    }

    private PreviewCardResponse toCard(long companyId, Campaign campaign, CampaignTarget target) {
        return new PreviewCardResponse(target, campaign.name(), findCallSinceClaim(companyId, target),
                clientMemoryUseCase.findByCompanyIdAndPhone(companyId, target.phone()));
    }

    /** The call placed for the card since this operator claimed it — the claim stamps its lease from that moment. */
    private Long findCallSinceClaim(long companyId, CampaignTarget target) {
        if (target.nextAttemptAt() == null) {
            return null;
        }
        Instant claimedAt = target.nextAttemptAt().minus(CampaignTarget.OPERATOR_CLAIM_LEASE);
        return callAttemptUseCase.findLatestAttemptIdByTargetIdSince(companyId, target.id(), claimedAt);
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

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }
}
