package uz.murodjon.robotcallv2.callrecord.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRetentionTest {

    private static final Instant NOW = Instant.parse("2026-09-14T03:30:00Z");

    @Test
    void countsTheDefaultPeriodAndTheZeroPiiGraceBackFromNow() {
        ConversationRetention retention = ConversationRetention.of(NOW, 90, Duration.ofHours(1));

        assertThat(retention.now()).isEqualTo(NOW);
        assertThat(retention.defaultCutoff()).isEqualTo(Instant.parse("2026-06-16T03:30:00Z"));
        assertThat(retention.zeroPiiCutoff()).isEqualTo(Instant.parse("2026-09-14T02:30:00Z"));
    }

    /** 0 keeps forever, as everywhere else in the retention settings — it never means "delete now". */
    @Test
    void zeroDaysKeepsTheConversationsOfCallsNoAgentRan() {
        ConversationRetention retention = ConversationRetention.of(NOW, 0, Duration.ofHours(1));

        assertThat(retention.defaultCutoff()).isEqualTo(ConversationRetention.KEEP_FOREVER);
        assertThat(retention.zeroPiiCutoff()).isEqualTo(Instant.parse("2026-09-14T02:30:00Z"));
    }
}
