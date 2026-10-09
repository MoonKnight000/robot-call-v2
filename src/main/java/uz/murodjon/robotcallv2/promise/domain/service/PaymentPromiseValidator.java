package uz.murodjon.robotcallv2.promise.domain.service;

import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class PaymentPromiseValidator {

    public static final int MAX_REMINDER_DAYS_BEFORE = 7;
    public static final int MAX_GRACE_DAYS = 14;
    /** An SMS longer than this is several billed parts; a reminder has no reason to be. */
    public static final int MAX_TEMPLATE_LENGTH = 300;
    public static final int MAX_STATS_DAYS = 366;

    private PaymentPromiseValidator() {
    }

    public static void validateSettings(PaymentPromiseSettings settings) {
        List<String> errors = new ArrayList<>();
        if (settings.reminderDaysBefore() < 0 || settings.reminderDaysBefore() > MAX_REMINDER_DAYS_BEFORE) {
            errors.add("reminderDaysBefore: 0–" + MAX_REMINDER_DAYS_BEFORE);
        }
        if (settings.graceDays() < 0 || settings.graceDays() > MAX_GRACE_DAYS) {
            errors.add("graceDays: 0–" + MAX_GRACE_DAYS);
        }
        String template = settings.reminderTemplate();
        if (template == null || template.isBlank() || template.length() > MAX_TEMPLATE_LENGTH) {
            errors.add("reminderTemplate: 1–" + MAX_TEMPLATE_LENGTH + " characters");
        }
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.PAYMENT_PROMISE_SETTINGS_INVALID, errors, errors.size());
        }
    }

    /** What a promise may be marked by hand: anything but {@code OPEN}, which it already is. */
    public static void validateManualStatus(PaymentPromiseStatus status) {
        if (status == PaymentPromiseStatus.OPEN) {
            throw new ValidationException(ErrorCode.PAYMENT_PROMISE_STATUS_INVALID, status);
        }
    }

    public static void validateStatsRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_STATS_DAYS) {
            throw new ValidationException(ErrorCode.PAYMENT_PROMISE_RANGE_INVALID, MAX_STATS_DAYS);
        }
    }
}
