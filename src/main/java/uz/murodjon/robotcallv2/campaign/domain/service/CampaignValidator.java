package uz.murodjon.robotcallv2.campaign.domain.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryPolicy;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.DialMode;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalTime;
import java.util.List;

@Component
public class CampaignValidator {

    /** Three Cyrillic SMS parts (3 × 153 characters) — longer is split into ever more paid parts. */
    static final int MAX_FOLLOW_UP_SMS_LENGTH = 459;

    /**
     * An AI campaign needs the agent that speaks; an operator campaign needs the queue whose
     * members take its calls, and may name an agent too. PREDICTIVE needs both: the agent takes
     * every answered client no operator is left for.
     */
    public void validateDialMode(DialMode dialMode, Long aiAgentId, Long callQueueId) {
        if ((dialMode.placesAiCalls() || dialMode == DialMode.PREDICTIVE) && aiAgentId == null) {
            throw new ValidationException(ErrorCode.CAMPAIGN_AI_AGENT_REQUIRED);
        }
        if (!dialMode.placesAiCalls() && callQueueId == null) {
            throw new ValidationException(ErrorCode.CAMPAIGN_CALL_QUEUE_REQUIRED, dialMode);
        }
    }

    /**
     * A follow-up SMS fits one message part set, and never goes to a number the call learned
     * it must not text: one that asked not to be contacted, or one that turned out to be
     * somebody else — a stranger would read the client's facts.
     */
    public void validateRetryPolicy(RetryPolicy policy) {
        policy.rules().forEach((disposition, rule) -> {
            if (rule == null || rule.smsText() == null || rule.smsText().isBlank()) {
                return;
            }
            if (disposition == Disposition.DO_NOT_CALL || disposition == Disposition.WRONG_NUMBER) {
                throw new ValidationException(ErrorCode.CAMPAIGN_FOLLOW_UP_SMS_NOT_ALLOWED, disposition);
            }
            if (rule.smsText().length() > MAX_FOLLOW_UP_SMS_LENGTH) {
                throw new ValidationException(ErrorCode.CAMPAIGN_FOLLOW_UP_SMS_TOO_LONG, disposition,
                        MAX_FOLLOW_UP_SMS_LENGTH);
            }
            if (rule.smsOnAttempt() != null && rule.smsOnAttempt() < 1) {
                throw new ValidationException(ErrorCode.CAMPAIGN_FOLLOW_UP_SMS_ATTEMPT_INVALID, disposition);
            }
        });
    }

    public void validateDialWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new ValidationException(ErrorCode.CAMPAIGN_DIAL_WINDOW_INVALID);
        }
        if (start.isAfter(end)) {
            throw new ValidationException(ErrorCode.CAMPAIGN_DIAL_WINDOW_INVALID);
        }
    }

    /**
     * Whether a target source describes something that can actually be fetched.
     *
     * <p>Bean validation cannot express this: a GENERIC source needs a url and no steps, a
     * CHAINED one needs steps and no url, and which of the two applies is another field of
     * the same request. Rejecting here means the failure arrives when the source is saved,
     * rather than at 09:00 the next morning as an empty campaign.
     */
    public void validateTargetSource(TargetSourceProvider provider, String url, List<TargetSourceStep> steps,
                                     String toolName) {
        if (provider == TargetSourceProvider.CHAINED) {
            validateSteps(steps);
            return;
        }
        if (provider == TargetSourceProvider.TOOL) {
            if (toolName == null || toolName.isBlank()) {
                throw new ValidationException(ErrorCode.TARGET_SOURCE_TOOL_REQUIRED);
            }
            return;
        }
        if (url == null || url.isBlank()) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_URL_INVALID, String.valueOf(url));
        }
    }

    /**
     * Every step needs an address, and only the first may list — a later step is called
     * once per row and its answer describes that one row, so an {@code itemsPath} there
     * would silently do nothing.
     */
    private void validateSteps(List<TargetSourceStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new ValidationException(ErrorCode.TARGET_SOURCE_STEPS_REQUIRED);
        }
        for (int index = 0; index < steps.size(); index++) {
            TargetSourceStep step = steps.get(index);
            String name = step.name() != null && !step.name().isBlank() ? step.name() : String.valueOf(index + 1);
            if (step.url() == null || step.url().isBlank()) {
                throw new ValidationException(ErrorCode.TARGET_SOURCE_STEP_URL_MISSING, name);
            }
            if (index > 0 && step.paginate()) {
                throw new ValidationException(ErrorCode.TARGET_SOURCE_STEP_PAGINATION_NOT_FIRST, name);
            }
            if (step.extract().isEmpty()) {
                throw new ValidationException(ErrorCode.TARGET_SOURCE_STEP_EXTRACTS_NOTHING, name);
            }
        }
    }

    public void validateRecurrence(RecurrenceType recurrenceType, Integer dayOfMonth, String cronExpression) {
        if (recurrenceType == RecurrenceType.MONTHLY && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new ValidationException(ErrorCode.CAMPAIGN_RECURRENCE_INVALID);
        }
        if (recurrenceType == RecurrenceType.CRON && (cronExpression == null || cronExpression.isBlank())) {
            throw new ValidationException(ErrorCode.CAMPAIGN_RECURRENCE_INVALID);
        }
    }
}
