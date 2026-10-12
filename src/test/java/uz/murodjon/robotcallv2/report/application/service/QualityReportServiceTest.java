package uz.murodjon.robotcallv2.report.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.QualityReportRepository;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;
import uz.murodjon.robotcallv2.shared.exception.AppException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QualityReportServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    private QualityReportRepository repository;
    private QualityReportService service;

    @BeforeEach
    void setUp() {
        repository = mock(QualityReportRepository.class);
        service = new QualityReportService(repository, mock(CompanyConfigUseCase.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void periodDefaultsToTheLastSevenDays() {
        service.findOperatorRating(COMPANY_ID, null, null);

        verify(repository).findOperatorQualityScores(COMPANY_ID, Instant.parse("2026-09-30T12:00:00Z"), NOW);
    }

    @Test
    void trendIsByDayUnlessAskedOtherwise() {
        service.findCriterionTrend(COMPANY_ID, null, null, null, null);

        verify(repository).findCriterionTrend(eq(COMPANY_ID), any(), any(), any(ZoneId.class), eq(TrendBucket.DAY),
                isNull());
    }

    @Test
    void topicLimitDefaultsToTwentyAndIsKeptWithinRange() {
        service.findTopics(COMPANY_ID, null, null, null, null);
        service.findTopics(COMPANY_ID, null, null, null, 0);
        service.findTopics(COMPANY_ID, null, null, 9L, 5000);

        verify(repository).findTopicCounts(eq(COMPANY_ID), any(), any(), isNull(), eq(20));
        verify(repository).findTopicCounts(eq(COMPANY_ID), any(), any(), isNull(), eq(1));
        verify(repository).findTopicCounts(eq(COMPANY_ID), any(), any(), eq(9L), eq(100));
    }

    @Test
    void periodThatEndsBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> service.findFailedCriteria(COMPANY_ID, "2026-10-07", "2026-10-01", null))
                .isInstanceOf(AppException.class);
    }
}
