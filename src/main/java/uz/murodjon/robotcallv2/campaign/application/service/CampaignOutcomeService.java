package uz.murodjon.robotcallv2.campaign.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.domain.service.RetrySchedule;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.*;
import java.util.Set;

/**
 * What a finished call does to its target and campaign: the target is done, retried later
 * — inside the campaign's dial window, over the company's holidays — or exhausted, and the
 * campaign completes once nothing is left to dial.
 *
 * <p>Used within the campaign feature only ({@link CampaignTargetService},
 * {@link CampaignService}); other features reach it through {@code CampaignTargetUseCase}.
 */
@Service
public class CampaignOutcomeService {

    private static final Logger log = LoggerFactory.getLogger(CampaignOutcomeService.class);

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final RetryProperties retryProperties;
    private final AuditUseCase auditUseCase;
    private final NotificationUseCase notificationUseCase;
    private final Clock clock;

    public CampaignOutcomeService(CampaignRepository campaignRepository,
                                  CampaignTargetRepository campaignTargetRepository,
                                  CompanyConfigUseCase companyConfigUseCase, RetryProperties retryProperties,
                                  AuditUseCase auditUseCase, NotificationUseCase notificationUseCase, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.companyConfigUseCase = companyConfigUseCase;
        this.retryProperties = retryProperties;
        this.auditUseCase = auditUseCase;
        this.notificationUseCase = notificationUseCase;
        this.clock = clock;
    }

    void applyOutcome(long companyId, long targetId, Disposition disposition) {
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
        if (disposition == Disposition.CALLBACK_REQUESTED && t.nextAttemptAt() != null && t.nextAttemptAt().isAfter(Instant.now(clock))) {
            log.info("Target {} already scheduled for smart callback at {}, preserving schedule (attempt {}/{})",
                    targetId, t.nextAttemptAt(), t.attempts(), maxAttempts);
            return;
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

    /**
     * An operator's wrap-up, in target terms. Not routed through {@link #applyOutcome}: there
     * a CALLBACK_REQUESTED with a future next_attempt_at keeps the target as it is, and an
     * operator's claim always carries one — its lease.
     */
    void settleByOperator(long companyId, long targetId, TargetStatus status, Instant callbackAt) {
        CampaignTarget t = campaignTargetRepository.find(companyId, targetId);
        if (t == null) {
            return;
        }
        if (status == TargetStatus.PENDING && callbackAt != null) {
            scheduleCallback(companyId, targetId, callbackAt);
            return;
        }
        Campaign c = campaignRepository.find(companyId, t.campaignId());
        TargetStatus settled = status;
        if (status == TargetStatus.PENDING && t.attempts() >= (c != null ? c.maxAttempts() : 3)) {
            settled = TargetStatus.EXHAUSTED;
        }
        if (settled == TargetStatus.PENDING) {
            Instant next = nextAttemptAt(Disposition.CALLBACK_REQUESTED, c);
            campaignTargetRepository.updateStatus(targetId, TargetStatus.PENDING, next);
            log.info("Target {} rescheduled by its operator -> {}", targetId, next);
            return;
        }
        campaignTargetRepository.updateStatus(targetId, settled, null);
        log.info("Target {} {} by its operator", targetId, settled);
        checkCompletion(companyId, t.campaignId());
    }

    void scheduleCallback(long companyId, long targetId, Instant callbackAt) {
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

    /**
     * One statement decides: two finalizers ending the last two calls at once both used to
     * read "no active targets" and both announced the campaign finished.
     */
    void checkCompletion(long companyId, long campaignId) {
        if (!campaignRepository.completeIfFinished(companyId, campaignId)) {
            return;
        }
        auditUseCase.record(companyId, "CAMPAIGN_COMPLETED", "campaign", String.valueOf(campaignId), null);
        Campaign c = campaignRepository.find(companyId, campaignId);
        String name = c != null ? c.name() : String.valueOf(campaignId);
        notificationUseCase.notify(companyId, NotificationType.CAMPAIGN_FINISHED,
                "Kampaniya tugadi", "\"" + name + "\" kampaniyasi barcha nishonlarni yakunladi", null);
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
