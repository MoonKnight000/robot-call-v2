package uz.murodjon.robotcallv2.promise.domain.service;

import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** When a promise is reminded of and when it is broken — the company's own date is passed in. */
public final class PaymentPromiseRules {

    /** An SMS goes out in daytime only: nobody wants a debt reminder at 23:40. */
    public static final LocalTime REMINDER_FROM = LocalTime.of(9, 0);
    public static final LocalTime REMINDER_UNTIL = LocalTime.of(20, 0);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private PaymentPromiseRules() {
    }

    /**
     * Not yet reminded, and today is between {@code daysBefore} days before the date and the
     * date itself — a promise recorded late (after the reminder day) is still reminded once.
     */
    public static boolean isReminderDue(PaymentPromise promise, int daysBefore, LocalDate today) {
        return promise.reminderSentAt() == null
                && !today.isBefore(promise.promisedDate().minusDays(daysBefore))
                && !today.isAfter(promise.promisedDate());
    }

    public static boolean isReminderHour(LocalTime now) {
        return !now.isBefore(REMINDER_FROM) && now.isBefore(REMINDER_UNTIL);
    }

    /** The date and the grace days after it are all behind us. */
    public static boolean isOverdue(PaymentPromise promise, int graceDays, LocalDate today) {
        return today.isAfter(promise.promisedDate().plusDays(graceDays));
    }

    /** The template with {@code {date}} and {@code {amount}} filled in; no amount leaves an empty place. */
    public static String renderReminder(String template, PaymentPromise promise) {
        BigDecimal amount = promise.promisedAmount();
        return template
                .replace("{date}", DATE.format(promise.promisedDate()))
                .replace("{amount}", amount != null ? amount.stripTrailingZeros().toPlainString() : "")
                .trim();
    }
}
