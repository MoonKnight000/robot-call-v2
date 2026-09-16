package uz.murodjon.robotcallv2.dialer.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ContactHistory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContactFrequencyPolicyTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final Instant NOW = Instant.parse("2026-09-12T08:00:00Z");

    private static Instant daysAgo(double days) {
        return NOW.minus(Duration.ofMinutes((long) (days * 24 * 60)));
    }

    @Test
    void nothingSwitchedOnAllowsTheCall() {
        ContactHistory busy = new ContactHistory(List.of(daysAgo(0.1), daysAgo(0.2)), daysAgo(0.1),
                LocalDate.of(2026, 9, 30));
        assertThat(ContactFrequencyPolicy.nextAllowed(0, 0, false, busy, NOW, TASHKENT)).isNull();
    }

    @Test
    void underTheCapAllowsTheCall() {
        ContactHistory history = new ContactHistory(List.of(daysAgo(1), daysAgo(2)), null, null);
        assertThat(ContactFrequencyPolicy.nextAllowed(3, 0, false, history, NOW, TASHKENT)).isNull();
    }

    @Test
    void atTheCapWaitsForTheOldestCountedAttemptToAgeOut() {
        // newest first; with a cap of 3 the third one (5 days ago) frees a slot in 2 days
        ContactHistory history = new ContactHistory(List.of(daysAgo(1), daysAgo(3), daysAgo(5)), null, null);
        assertThat(ContactFrequencyPolicy.nextAllowed(3, 0, false, history, NOW, TASHKENT))
                .isEqualTo(daysAgo(5).plus(Duration.ofDays(7)));
    }

    @Test
    void aRecentConversationStartsAQuietPeriod() {
        ContactHistory history = new ContactHistory(List.of(), daysAgo(2), null);
        assertThat(ContactFrequencyPolicy.nextAllowed(0, 7, false, history, NOW, TASHKENT))
                .isEqualTo(daysAgo(2).plus(Duration.ofDays(7)));
        assertThat(ContactFrequencyPolicy.nextAllowed(0, 1, false, history, NOW, TASHKENT)).isNull();
    }

    @Test
    void aPromiseHoldsCallsUntilTheDayAfterItsDate() {
        ContactHistory history = new ContactHistory(List.of(), null, LocalDate.of(2026, 9, 15));
        assertThat(ContactFrequencyPolicy.nextAllowed(0, 0, true, history, NOW, TASHKENT))
                .isEqualTo(LocalDate.of(2026, 9, 16).atStartOfDay(TASHKENT).toInstant());
    }

    @Test
    void aLapsedPromiseHoldsNothing() {
        ContactHistory history = new ContactHistory(List.of(), null, LocalDate.of(2026, 9, 1));
        assertThat(ContactFrequencyPolicy.nextAllowed(0, 0, true, history, NOW, TASHKENT)).isNull();
    }

    @Test
    void theStrictestRuleWins() {
        ContactHistory history = new ContactHistory(List.of(daysAgo(6.5)), daysAgo(1), LocalDate.of(2026, 9, 20));
        assertThat(ContactFrequencyPolicy.nextAllowed(1, 3, true, history, NOW, TASHKENT))
                .isEqualTo(LocalDate.of(2026, 9, 21).atStartOfDay(TASHKENT).toInstant());
    }
}
