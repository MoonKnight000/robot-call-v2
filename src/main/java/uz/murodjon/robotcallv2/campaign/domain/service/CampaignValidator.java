package uz.murodjon.robotcallv2.campaign.domain.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalTime;

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

    public void validateRecurrence(RecurrenceType recurrenceType, Integer dayOfMonth, String cronExpression) {
        if (recurrenceType == RecurrenceType.MONTHLY && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new ValidationException(ErrorCode.CAMPAIGN_RECURRENCE_INVALID);
        }
        if (recurrenceType == RecurrenceType.CRON && (cronExpression == null || cronExpression.isBlank())) {
            throw new ValidationException(ErrorCode.CAMPAIGN_RECURRENCE_INVALID);
        }
    }
}
