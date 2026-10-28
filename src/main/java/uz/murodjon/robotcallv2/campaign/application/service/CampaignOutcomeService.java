package uz.murodjon.robotcallv2.campaign.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.murodjon.robotcallv2.agent.dialog.PromptTemplateEngine;
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
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.*;
import java.util.HashMap;
import java.util.Map;
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

    private static final TypeReference<Map<String, Object>> FACTS = new TypeReference<>() {
    };

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final RetryProperties retryProperties;
    private final AuditUseCase auditUseCase;
    private final NotificationUseCase notificationUseCase;
    private final SmsUseCase smsUseCase;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CampaignOutcomeService(CampaignRepository campaignRepository,
                                  CampaignTargetRepository campaignTargetRepository,
                                  CompanyConfigUseCase companyConfigUseCase, RetryProperties retryProperties,
                                  AuditUseCase auditUseCase, NotificationUseCase notificationUseCase,
                                  SmsUseCase smsUseCase, ObjectMapper objectMapper, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.companyConfigUseCase = companyConfigUseCase;
        this.retryProperties = retryProperties;
        this.auditUseCase = auditUseCase;
        this.notificationUseCase = notificationUseCase;
        this.smsUseCase = smsUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    void applyOutcome(long companyId, long targetId, Disposition disposition) {
        CampaignTarget t = campaignTargetRepository.find(companyId, targetId);
        if (t == null) {
            return;
        }
        Campaign c = campaignRepository.find(companyId, t.campaignId());
        sendFollowUpSms(c, t, disposition);
        if (isTerminal(disposition)) {
            campaignTargetRepository.updateStatus(targetId, TargetStatus.DONE, null);
            log.info("Target {} DONE ({})", targetId, disposition);
            checkCompletion(companyId, t.campaignId());
            return;
        }
        int maxAttempts = c != null ? c.retry().maxAttempts() : 3;
        // A disposition rule may give up sooner — "one voicemail is enough" — never later
        // than the campaign's own cap.
        RetryRule rule = c != null ? c.retry().policy().ruleFor(disposition) : null;
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
     * Texts the number when the campaign's rule for this outcome says so — the second channel
     * of a follow-up: "no answer → SMS → call again later". Never fails the outcome: the target
     * moves on whether or not the SMS gateway answered.
     */
    private void sendFollowUpSms(Campaign campaign, CampaignTarget target, Disposition disposition) {
        RetryRule rule = campaign != null ? campaign.retry().policy().ruleFor(disposition) : null;
        if (rule == null || rule.smsText() == null || rule.smsText().isBlank()) {
            return;
        }
        // Checked on save as well (CampaignValidator); a stranger or a "do not contact" is never texted.
        if (target.doNotCall() || disposition == Disposition.DO_NOT_CALL || disposition == Disposition.WRONG_NUMBER) {
            return;
        }
        if (rule.smsOnAttempt() != null && rule.smsOnAttempt() != target.attempts()) {
            return;
        }
        if (!smsUseCase.isEnabled()) {
            log.debug("Follow-up SMS for target {} skipped: SMS is not enabled", target.id());
            return;
        }
        String text = PromptTemplateEngine.render(rule.smsText(), findFacts(target));
        try {
            boolean sent = smsUseCase.sendSms(campaign.companyId(), new SmsSendRequest(target.phone(), text));
            log.info("Follow-up SMS after {} to {} (target {}, attempt {}): {}", disposition,
                    PhoneMasker.mask(target.phone()), target.id(), target.attempts(), sent ? "sent" : "not sent");
        } catch (Exception e) {
            log.warn("Follow-up SMS to target {} failed: {}", target.id(), e.getMessage());
        }
    }

    /** The target's facts for the SMS template, plus {@code phone}; unreadable facts leave only the phone. */
    private Map<String, Object> findFacts(CampaignTarget target) {
        Map<String, Object> facts = new HashMap<>();
        if (target.contextData() != null && !target.contextData().isBlank()) {
            try {
                facts.putAll(objectMapper.readValue(target.contextData(), FACTS));
            } catch (Exception e) {
                log.debug("Target {} facts are not a JSON object: {}", target.id(), e.getMessage());
            }
        }
        facts.put("phone", target.phone());
        return facts;
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
        if (status == TargetStatus.PENDING && t.attempts() >= (c != null ? c.retry().maxAttempts() : 3)) {
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
            finalInstant = RetrySchedule.intoWindow(candidate, c.schedule().allowedDays(),
                    c.schedule().dialWindowStart(), c.schedule().dialWindowEnd(), holidaysOf(companyId)).toInstant();
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
        int retryMinutes = campaign != null ? campaign.retry().intervalMinutes() : 0;
        RetryRule rule = campaign != null ? campaign.retry().policy().ruleFor(disposition) : null;
        Duration delay = RetrySchedule.delayFor(disposition, retryProperties.toRetryDelays(), retryMinutes, rule);
        ZonedDateTime candidate = ZonedDateTime.now(clock).plus(delay);
        if (campaign == null || !retryProperties.respectDialWindow()) {
            return candidate.toInstant();
        }
        return RetrySchedule.intoWindow(candidate, campaign.schedule().allowedDays(),
                campaign.schedule().dialWindowStart(), campaign.schedule().dialWindowEnd(), holidaysOf(campaign.companyId())).toInstant();
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
