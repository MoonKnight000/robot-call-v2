package uz.murodjon.robotcallv2.promise.domain.entity;

/**
 * How a company follows up its promises to pay. Both automatic actions are off until the
 * company turns them on: an SMS costs money, and a call back is a call the company pays for.
 *
 * @param reminderEnabled    send an SMS before the promised date
 * @param reminderDaysBefore how many days before the date; 0 — on the day itself
 * @param reminderTemplate   the SMS text; {@code {date}} and {@code {amount}} are filled in
 * @param graceDays          days after the date a payment may still arrive before the promise is broken
 * @param recallOnBroken     a broken promise puts its campaign target back in the dialing queue
 * @param paymentGoalKey     the conversion goal that counts as a payment; null — any reported event
 */
public record PaymentPromiseSettings(
        long companyId,
        boolean reminderEnabled,
        int reminderDaysBefore,
        String reminderTemplate,
        int graceDays,
        boolean recallOnBroken,
        String paymentGoalKey
) {

    public static final String DEFAULT_REMINDER_TEMPLATE =
            "Hurmatli mijoz, {date} gacha to'lov qilishga va'da berganingizni eslatib o'tamiz. Rahmat!";

    /** What a company that never saved its settings gets. */
    public static PaymentPromiseSettings defaultsFor(long companyId) {
        return new PaymentPromiseSettings(companyId, false, 1, DEFAULT_REMINDER_TEMPLATE, 1, false, null);
    }
}
