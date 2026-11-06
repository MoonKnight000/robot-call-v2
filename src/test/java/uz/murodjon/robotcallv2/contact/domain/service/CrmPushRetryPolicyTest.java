package uz.murodjon.robotcallv2.contact.domain.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CrmPushRetryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void waitsTwiceAsLongAfterEachFailureUpToSixHours() {
        assertThat(CrmPushRetryPolicy.findNextAttemptAt(NOW, 1)).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(CrmPushRetryPolicy.findNextAttemptAt(NOW, 2)).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(CrmPushRetryPolicy.findNextAttemptAt(NOW, 4)).isEqualTo(NOW.plus(Duration.ofMinutes(8)));
        assertThat(CrmPushRetryPolicy.findNextAttemptAt(NOW, 30)).isEqualTo(NOW.plus(Duration.ofHours(6)));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        assertThat(CrmPushRetryPolicy.isExhausted(CrmPushRetryPolicy.MAX_ATTEMPTS - 1)).isFalse();
        assertThat(CrmPushRetryPolicy.isExhausted(CrmPushRetryPolicy.MAX_ATTEMPTS)).isTrue();
    }
}
