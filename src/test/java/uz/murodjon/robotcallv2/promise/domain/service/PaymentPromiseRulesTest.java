package uz.murodjon.robotcallv2.promise.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPromiseRulesTest {

    private static final LocalDate PROMISED = LocalDate.of(2026, 10, 15);

    @Test
    void theReminderIsDueFromItsDayUntilTheDate() {
        PaymentPromise promise = promise(null, null);

        assertThat(PaymentPromiseRules.isReminderDue(promise, 1, PROMISED.minusDays(2))).isFalse();
        assertThat(PaymentPromiseRules.isReminderDue(promise, 1, PROMISED.minusDays(1))).isTrue();
        assertThat(PaymentPromiseRules.isReminderDue(promise, 1, PROMISED)).isTrue();
        assertThat(PaymentPromiseRules.isReminderDue(promise, 1, PROMISED.plusDays(1))).isFalse();
    }

    @Test
    void aRemindedPromiseIsNotRemindedAgain() {
        assertThat(PaymentPromiseRules.isReminderDue(promise(null, Instant.now()), 1, PROMISED)).isFalse();
    }

    @Test
    void aPromiseIsOverdueOnlyAfterItsGraceDays() {
        PaymentPromise promise = promise(null, null);

        assertThat(PaymentPromiseRules.isOverdue(promise, 2, PROMISED.plusDays(2))).isFalse();
        assertThat(PaymentPromiseRules.isOverdue(promise, 2, PROMISED.plusDays(3))).isTrue();
    }

    @Test
    void remindersGoOutInDaytimeOnly() {
        assertThat(PaymentPromiseRules.isReminderHour(LocalTime.of(8, 59))).isFalse();
        assertThat(PaymentPromiseRules.isReminderHour(LocalTime.of(9, 0))).isTrue();
        assertThat(PaymentPromiseRules.isReminderHour(LocalTime.of(20, 0))).isFalse();
    }

    @Test
    void theTemplateGetsTheDateAndTheAmount() {
        assertThat(PaymentPromiseRules.renderReminder("{date} gacha {amount} so'm",
                promise(new BigDecimal("500000.00"), null))).isEqualTo("15.10.2026 gacha 500000 so'm");
        assertThat(PaymentPromiseRules.renderReminder("{date} gacha", promise(null, null)))
                .isEqualTo("15.10.2026 gacha");
    }

    private static PaymentPromise promise(BigDecimal amount, Instant reminderSentAt) {
        return new PaymentPromise(1L, 1L, 10L, 20L, "+998901234567", PROMISED, amount, PaymentPromiseStatus.OPEN,
                reminderSentAt, null, Instant.parse("2026-10-09T10:00:00Z"));
    }
}
