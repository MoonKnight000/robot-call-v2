package uz.murodjon.robotcallv2.campaign.domain.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.TargetSourceStep;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceProvider;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalTime;
import java.util.List;

@Component
public class CampaignValidator {

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
    public void validateTargetSource(TargetSourceProvider provider, String url, List<TargetSourceStep> steps) {
        if (provider == TargetSourceProvider.CHAINED) {
            validateSteps(steps);
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
                throw new ValidationException(ErrorCode.TARGET_SOURCE_STEP_INVALID, name, "no url");
            }
            if (index > 0 && step.paginate()) {
                throw new ValidationException(ErrorCode.TARGET_SOURCE_STEP_INVALID, name,
                        "only the first step may paginate");
            }
            if (step.extract().isEmpty()) {
                throw new ValidationException(ErrorCode.TARGET_SOURCE_STEP_INVALID, name,
                        "extracts nothing, so it cannot affect the list");
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
