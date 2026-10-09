package uz.murodjon.robotcallv2.promise.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A full replace of the company's promise follow-up settings.
 *
 * @param reminderTemplate null — the default text
 * @param paymentGoalKey   null or blank — any reported conversion counts as a payment
 */
public record UpdatePaymentPromiseSettingsRequest(
        @NotNull Boolean reminderEnabled,
        @NotNull Integer reminderDaysBefore,
        String reminderTemplate,
        @NotNull Integer graceDays,
        @NotNull Boolean recallOnBroken,
        @Size(max = 64) String paymentGoalKey
) {
}
