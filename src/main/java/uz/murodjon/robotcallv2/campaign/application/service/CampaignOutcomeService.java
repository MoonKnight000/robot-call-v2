package uz.murodjon.robotcallv2.campaign.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import uz.murodjon.robotcallv2.agent.dialog.PromptTemplateEngine;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.output.AutoEnrollContactRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignTargetRepository;
import uz.murodjon.robotcallv2.campaign.application.port.output.OutcomeClassifierPort;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.entity.StepAttempt;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeContactUpdate;
import uz.murodjon.robotcallv2.campaign.domain.entity.OutcomeRoute;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceOutcome;
import uz.murodjon.robotcallv2.campaign.domain.entity.SequenceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeEvaluation;
import uz.murodjon.robotcallv2.campaign.domain.enums.OutcomeExit;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceStepType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.campaign.domain.service.OutcomeMatcher;
import uz.murodjon.robotcallv2.campaign.domain.service.RetrySchedule;
import uz.murodjon.robotcallv2.campaign.domain.service.SequenceRouting;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.campaign.infrastructure.config.RetryProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;
import uz.murodjon.robotcallv2.sms.application.dto.SmsSendRequest;
import uz.murodjon.robotcallv2.sms.application.port.input.SmsUseCase;

import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** The built-in outcomes every CONVERSATION step has, as a target records them. */
    static final String OTHER = "Other";
    static final String NO_RESPONSE = "No Response";
    /** A callback the lead gave no time for: Retell's "next business day", at least 24 hours on. */
    private static final Duration CALLBACK_FALLBACK = Duration.ofHours(24);

    private final CampaignRepository campaignRepository;
    private final CampaignTargetRepository campaignTargetRepository;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final RetryProperties retryProperties;
    private final AuditUseCase auditUseCase;
    private final NotificationUseCase notificationUseCase;
    private final SmsUseCase smsUseCase;
    private final OutcomeClassifierPort outcomeClassifierPort;
    private final AutoEnrollContactRepository autoEnrollContactRepository;
    private final DoNotCallUseCase doNotCallUseCase;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CampaignOutcomeService(CampaignRepository campaignRepository,
                                  CampaignTargetRepository campaignTargetRepository,
                                  CompanyConfigUseCase companyConfigUseCase,
                                  BusinessScheduleUseCase businessScheduleUseCase,
                                  RetryProperties retryProperties,
                                  AuditUseCase auditUseCase, NotificationUseCase notificationUseCase,
                                  SmsUseCase smsUseCase, OutcomeClassifierPort outcomeClassifierPort,
                                  AutoEnrollContactRepository autoEnrollContactRepository,
                                  DoNotCallUseCase doNotCallUseCase, ObjectMapper objectMapper, Clock clock) {
        this.outcomeClassifierPort = outcomeClassifierPort;
        this.autoEnrollContactRepository = autoEnrollContactRepository;
        this.doNotCallUseCase = doNotCallUseCase;
        this.campaignRepository = campaignRepository;
        this.campaignTargetRepository = campaignTargetRepository;
        this.companyConfigUseCase = companyConfigUseCase;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.retryProperties = retryProperties;
        this.auditUseCase = auditUseCase;
        this.notificationUseCase = notificationUseCase;
        this.smsUseCase = smsUseCase;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param transcript the call's transcript, on which a sequence's PROMPT outcomes are judged; null
     *                   when there is none — no AI agent spoke — and only METRICS outcomes can match
     */
    void applyOutcome(long companyId, long targetId, Disposition disposition, String transcript) {
        CampaignTarget t = campaignTargetRepository.find(companyId, targetId);
        if (t == null) {
            return;
        }
        Campaign c = campaignRepository.find(companyId, t.campaignId());
        // Before the follow-up SMS: the second dial is part of the same attempt, not its end.
        if (c != null && c.sequence() != null && doubleDial(c, t, disposition)) {
            return;
        }
        sendFollowUpSms(c, t, disposition);
        if (c != null && c.sequence() != null) {
            applySequenceOutcome(c, t, disposition, transcript);
            return;
        }
        if (SequenceRouting.isFinal(disposition)) {
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
     * A finished call on a campaign that has a sequence (Retell's outcome evaluation). An unanswered
     * call retries the step until its attempts are spent, then follows the step's No Response route.
     * An answered one is judged: the step's own outcomes and then the global ones, METRICS first in
     * list order, then every PROMPT outcome together through the LLM classifier; nothing matching
     * follows the step's Other route.
     */
    private void applySequenceOutcome(Campaign campaign, CampaignTarget target, Disposition disposition,
                                      String transcript) {
        CampaignSequence sequence = campaign.sequence();
        SequenceStep step = currentStep(sequence, target);
        if (step == null || SequenceRouting.endsSequence(disposition)) {
            // No step to route on (a sequence edited under a live campaign), or a number never to be
            // tried again: DO_NOT_CALL was listed at hang-up, a stranger or rejected number is dropped.
            finishTarget(campaign, target, disposition);
            return;
        }
        if (disposition == Disposition.CALLBACK_REQUESTED && campaign.autoRetries().autoScheduleCallbacks()) {
            scheduleSequenceCallback(campaign, target, step);
            return;
        }
        // With automatic callbacks off, a lead who asked to be called back is an answered call like
        // any other: the outcomes decide (a "Callback" outcome can route it), as in Retell.
        if (!SequenceRouting.isAnswered(disposition)) {
            if (retryStep(campaign, target, step, disposition)) {
                return;
            }
            follow(campaign, target, step, step.outcomes().noResponse(), NO_RESPONSE, false);
            return;
        }
        SequenceOutcome reached = findReachedOutcome(campaign, target, step, disposition, transcript);
        if (reached == null) {
            follow(campaign, target, step, step.outcomes().other(), OTHER, true);
            return;
        }
        saveToContact(campaign, target, reached);
        follow(campaign, target, step, reached.next(), reached.name(), true);
    }

    /**
     * Retell's double dial: an unanswered call gets one immediate second dial as part of the same
     * attempt — the attempt is handed back, so the step's count does not move. Never after a
     * voicemail (the disposition is not NO_ANSWER), on a campaign that is no longer running, or a
     * second time on the same attempt.
     *
     * @return true when the second dial was queued and nothing else is to happen to the target
     */
    private boolean doubleDial(Campaign campaign, CampaignTarget target, Disposition disposition) {
        if (disposition != Disposition.NO_ANSWER || !campaign.autoRetries().doubleDial()
                || campaign.status() != CampaignStatus.ACTIVE) {
            return false;
        }
        if (!campaignTargetRepository.redialOnce(target.id())) {
            return false;
        }
        log.info("Target {} double dialled on attempt {}", target.id(), target.attempts());
        return true;
    }

    /**
     * Retell's auto-scheduled callback: at the time the lead asked for (the agent's callback tool put
     * it on the target while the call was on), else at the first time in the calling window at least
     * 24 hours later. The step's attempts start over from attempt 1.
     */
    private void scheduleSequenceCallback(Campaign campaign, CampaignTarget target, SequenceStep step) {
        Instant now = Instant.now(clock);
        Instant at = target.nextAttemptAt() != null && target.nextAttemptAt().isAfter(now)
                ? target.nextAttemptAt()
                : intoDialWindow(campaign, ZonedDateTime.now(clock).plus(CALLBACK_FALLBACK));
        campaignTargetRepository.moveToStep(target.id(), step.id(), at);
        log.info("Target {} asked for a callback on step {} -> {} (attempts start over)", target.id(), step.id(), at);
    }

    /**
     * Places the step's next attempt, its own delay after this call (Retell: counted from the
     * previous attempt, and moved to the next opening of the calling window); false when the
     * step's attempts are spent.
     */
    private boolean retryStep(Campaign campaign, CampaignTarget target, SequenceStep step, Disposition disposition) {
        List<StepAttempt> attempts = step.conversation() != null ? step.conversation().attempts() : List.of();
        int maxAttempts = Math.max(1, attempts.size());
        RetryRule rule = campaign.retry().policy().ruleFor(disposition);
        if (rule != null && rule.maxAttempts() != null && rule.maxAttempts() > 0) {
            maxAttempts = Math.min(maxAttempts, rule.maxAttempts());
        }
        if (target.stepAttempts() >= maxAttempts) {
            return false;
        }
        // stepAttempts calls were placed on this step, so the next one is attempts[stepAttempts].
        long delayMinutes = target.stepAttempts() < attempts.size()
                ? attempts.get(target.stepAttempts()).computeDelayMinutes() : 0;
        Instant next = delayMinutes > 0
                ? intoDialWindow(campaign, ZonedDateTime.now(clock).plusMinutes(delayMinutes))
                : nextAttemptAt(disposition, campaign, campaign.retry().intervalMinutes());
        campaignTargetRepository.updateStatus(target.id(), TargetStatus.PENDING, next);
        log.info("Target {} retries step {} ({}, attempt {}/{}) -> {}", target.id(), step.id(), disposition,
                target.stepAttempts(), maxAttempts, next);
        return true;
    }

    /** The outcome an answered call reached, or null when none matched (the step's Other). */
    private SequenceOutcome findReachedOutcome(Campaign campaign, CampaignTarget target, SequenceStep step,
                                               Disposition disposition, String transcript) {
        List<SequenceOutcome> candidates = new ArrayList<>(step.outcomes().outcomes());
        candidates.addAll(campaign.sequence().globalOutcomes());
        Map<String, Object> variables = findFacts(target);
        variables.put("disposition", disposition.name());
        Optional<SequenceOutcome> byMetrics = OutcomeMatcher.findMetricsMatch(candidates, variables);
        if (byMetrics.isPresent()) {
            return byMetrics.get();
        }
        List<SequenceOutcome> prompts = candidates.stream()
                .filter(outcome -> outcome.evaluation() == OutcomeEvaluation.PROMPT)
                .toList();
        if (prompts.isEmpty() || transcript == null || transcript.isBlank()) {
            return null;
        }
        String id = outcomeClassifierPort.findMatchingOutcomeId(campaign.companyId(), transcript, prompts);
        return id == null ? null : prompts.stream().filter(outcome -> outcome.id().equals(id)).findFirst().orElse(null);
    }

    /** What the outcome writes to the contact: the do-not-call list and its tags. Never fails the outcome. */
    private void saveToContact(Campaign campaign, CampaignTarget target, SequenceOutcome outcome) {
        OutcomeContactUpdate update = outcome.saveToContact();
        if (update.doNotCall() && !target.doNotCall()) {
            try {
                doNotCallUseCase.add(campaign.companyId(), target.phone(), "sequence outcome " + outcome.name(),
                        DoNotCallSource.CALL);
                campaignTargetRepository.setDoNotCall(campaign.companyId(), target.id());
            } catch (AppException e) {
                log.warn("Target {}: do-not-call entry not recorded: {}", target.id(), e.getMessage());
            }
        }
        for (String tag : update.tags()) {
            autoEnrollContactRepository.addTag(campaign.companyId(), target.phone(), tag);
        }
    }

    /**
     * Records the outcome and takes the route: to a step, or out of the sequence — DONE when somebody
     * answered, EXHAUSTED when nobody did. A global outcome that leads back to the step the target is
     * on ends the sequence rather than run the step again.
     */
    private void follow(Campaign campaign, CampaignTarget target, SequenceStep step, OutcomeRoute route,
                        String outcomeName, boolean answered) {
        boolean converted = route.exit() == OutcomeExit.END_SUCCESSFUL;
        campaignTargetRepository.recordOutcome(target.id(), outcomeName, converted);
        if (route.exit() == OutcomeExit.STEP && route.stepId() != null && !route.stepId().equals(step.id())) {
            log.info("Target {} reached '{}' on step {} -> step {}", target.id(), outcomeName, step.id(), route.stepId());
            advanceTo(campaign, target, route.stepId(), Duration.ofMinutes(route.delayMinutes()));
            return;
        }
        TargetStatus status = answered ? TargetStatus.DONE : TargetStatus.EXHAUSTED;
        campaignTargetRepository.updateStatus(target.id(), status, null);
        log.info("Target {} reached '{}' on step {} -> {}{}", target.id(), outcomeName, step.id(), status,
                converted ? " (converted)" : "");
        checkCompletion(campaign.companyId(), campaign.id());
    }

    /** The step the target is on; the start step when it has none yet or its step is gone. */
    private static SequenceStep currentStep(CampaignSequence sequence, CampaignTarget target) {
        SequenceStep step = target.stepId() != null ? sequence.findStep(target.stepId()).orElse(null) : null;
        return step != null ? step : sequence.findStep(sequence.startStepId()).orElse(null);
    }

    /** The target leaves the sequence: DONE when the call settled it, EXHAUSTED when attempts ran out. */
    private void finishTarget(Campaign campaign, CampaignTarget target, Disposition disposition) {
        TargetStatus status = SequenceRouting.isFinal(disposition) ? TargetStatus.DONE : TargetStatus.EXHAUSTED;
        campaignTargetRepository.updateStatus(target.id(), status, null);
        log.info("Target {} {} ({}, step {})", target.id(), status, disposition, target.stepId());
        checkCompletion(campaign.companyId(), campaign.id());
    }

    /**
     * Moves the target on to {@code stepId}. SMS steps are sent on the way and WAIT steps add to
     * the delay, so the target always lands on the next CONVERSATION step — or leaves the
     * sequence when the chain runs out.
     */
    private void advanceTo(Campaign campaign, CampaignTarget target, String stepId, Duration delay) {
        CampaignSequence sequence = campaign.sequence();
        SequenceStep step = sequence.findStep(stepId).orElse(null);
        // The validator keeps a sequence acyclic; the bound only guards a row edited by hand.
        for (int hops = 0; step != null && step.type() != SequenceStepType.CONVERSATION
                && hops <= sequence.steps().size(); hops++) {
            if (step.type() == SequenceStepType.SMS && !target.doNotCall()) {
                textTarget(campaign, target, step.smsText(), "sequence step " + step.id());
            } else if (step.type() == SequenceStepType.WAIT && step.waitMinutes() != null) {
                delay = delay.plusMinutes(step.waitMinutes());
            }
            step = step.next() != null ? sequence.findStep(step.next()).orElse(null) : null;
        }
        if (step == null || step.type() != SequenceStepType.CONVERSATION) {
            campaignTargetRepository.updateStatus(target.id(), TargetStatus.DONE, null);
            log.info("Target {} DONE: its sequence ended", target.id());
            checkCompletion(campaign.companyId(), campaign.id());
            return;
        }
        Instant dueAt = delay.isZero() ? null : intoDialWindow(campaign, ZonedDateTime.now(clock).plus(delay));
        campaignTargetRepository.moveToStep(target.id(), step.id(), dueAt);
        log.info("Target {} moves to step {} -> {}", target.id(), step.id(), dueAt);
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
        textTarget(campaign, target, rule.smsText(), "outcome " + disposition + ", attempt " + target.attempts());
    }

    /** Renders {@code template} with the target's facts and sends it; a gateway failure is logged, never thrown. */
    private void textTarget(Campaign campaign, CampaignTarget target, String template, String reason) {
        if (!smsUseCase.isEnabled()) {
            log.debug("SMS for target {} skipped: SMS is not enabled", target.id());
            return;
        }
        String text = PromptTemplateEngine.render(template, findFacts(target));
        try {
            boolean sent = smsUseCase.sendSms(campaign.companyId(), new SmsSendRequest(target.phone(), text));
            log.info("SMS to {} (target {}, {}): {}", PhoneMasker.mask(target.phone()), target.id(), reason,
                    sent ? "sent" : "not sent");
        } catch (Exception e) {
            log.warn("SMS to target {} failed: {}", target.id(), e.getMessage());
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
        LocalDate today = LocalDate.now(clock.withZone(zoneOf(companyId)));
        return businessScheduleUseCase.findHolidayDates(companyId, today, today.plusDays(365));
    }

    private Instant nextAttemptAt(Disposition disposition, Campaign campaign) {
        return nextAttemptAt(disposition, campaign, campaign != null ? campaign.retry().intervalMinutes() : 0);
    }

    /** {@code retryMinutes} stands in for the campaign's interval — a sequence step brings its own. */
    private Instant nextAttemptAt(Disposition disposition, Campaign campaign, int retryMinutes) {
        RetryRule rule = campaign != null ? campaign.retry().policy().ruleFor(disposition) : null;
        Duration delay = RetrySchedule.delayFor(disposition, retryProperties.toRetryDelays(), retryMinutes, rule);
        ZonedDateTime candidate = ZonedDateTime.now(clock).plus(delay);
        if (campaign == null) {
            return candidate.toInstant();
        }
        return intoDialWindow(campaign, candidate);
    }

    /** {@code candidate}, or the next moment the campaign may dial when it falls outside its window. */
    private Instant intoDialWindow(Campaign campaign, ZonedDateTime candidate) {
        if (!retryProperties.respectDialWindow()) {
            return candidate.toInstant();
        }
        return RetrySchedule.intoWindow(candidate, campaign.schedule().allowedDays(),
                campaign.schedule().dialWindowStart(), campaign.schedule().dialWindowEnd(), holidaysOf(campaign.companyId())).toInstant();
    }
}
