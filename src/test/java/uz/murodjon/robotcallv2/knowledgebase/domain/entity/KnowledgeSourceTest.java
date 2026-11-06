package uz.murodjon.robotcallv2.knowledgebase.domain.entity;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Auto-retry must only re-queue transient failures, with a doubling wait and a ceiling. */
class KnowledgeSourceTest {

    private static final Instant T0 = Instant.parse("2026-11-06T10:00:00Z");
    private static final Duration BASE = Duration.ofMinutes(5);

    @Test
    void transientFailureIsRetriedAfterTheBaseWait() {
        KnowledgeSource failed = pending().failed("KNOWLEDGE_SOURCE_FETCH_FAILED", "503", T0);

        assertThat(failed.failedAttempts()).isEqualTo(1);
        assertThat(failed.isDueForAutoRetry(T0.plus(Duration.ofMinutes(4)), 3, BASE)).isFalse();
        assertThat(failed.isDueForAutoRetry(T0.plus(Duration.ofMinutes(5)), 3, BASE)).isTrue();
    }

    @Test
    void waitDoublesWithEachConsecutiveFailure() {
        KnowledgeSource twice = pending()
                .failed("KNOWLEDGE_SOURCE_FETCH_FAILED", "503", T0)
                .queuedForIndexing()
                .failed("KNOWLEDGE_SOURCE_FETCH_FAILED", "503", T0);

        assertThat(twice.failedAttempts()).isEqualTo(2);
        assertThat(twice.isDueForAutoRetry(T0.plus(Duration.ofMinutes(9)), 3, BASE)).isFalse();
        assertThat(twice.isDueForAutoRetry(T0.plus(Duration.ofMinutes(10)), 3, BASE)).isTrue();
    }

    @Test
    void stopsAfterMaxAttemptsAndSkipsPermanentFailures() {
        KnowledgeSource source = pending();
        for (int i = 0; i < 4; i++) {
            source = source.queuedForIndexing().failed("KNOWLEDGE_SOURCE_FETCH_FAILED", "503", T0);
        }
        assertThat(source.isDueForAutoRetry(T0.plus(Duration.ofDays(1)), 3, BASE)).isFalse();

        KnowledgeSource empty = pending().failed("KNOWLEDGE_SOURCE_EMPTY", "no text", T0);
        assertThat(empty.isDueForAutoRetry(T0.plus(Duration.ofDays(1)), 3, BASE)).isFalse();
    }

    @Test
    void indexingResetsTheFailureCount() {
        KnowledgeSource recovered = pending().failed("KNOWLEDGE_SOURCE_FETCH_FAILED", "503", T0).indexed(4, T0);

        assertThat(recovered.failedAttempts()).isZero();
        assertThat(recovered.failedAt()).isNull();
    }

    private static KnowledgeSource pending() {
        return new KnowledgeSource(1L, 7L, null, "Narxlar", KnowledgeSourceType.URL, null, null,
                "https://example.uz/prices", KnowledgeSourceStatus.PENDING, null, null, null, 0, T0,
                null, null, null, null, 0, null, true);
    }
}
