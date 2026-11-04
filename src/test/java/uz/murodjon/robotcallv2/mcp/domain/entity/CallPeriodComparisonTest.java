package uz.murodjon.robotcallv2.mcp.domain.entity;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardOutcome;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardTotals;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallPeriodComparisonTest {

    @Test
    void differencesAreComputedInPercentAndPoints() {
        ReportSummary current = summary(200, 120, 90.0, List.of(new DashboardOutcome("TRANSFERRED", 40),
                new DashboardOutcome("PROMISE_TO_PAY", 60)));
        ReportSummary previous = summary(100, 50, 60.0, List.of(new DashboardOutcome("TRANSFERRED", 10),
                new DashboardOutcome("REFUSED", 20)));

        CallPeriodComparison comparison = CallPeriodComparison.of(current, previous);

        assertThat(comparison.totalCallsChangePct()).isEqualTo(100.0);
        assertThat(comparison.answerRatePct()).isEqualTo(60.0);
        assertThat(comparison.previousAnswerRatePct()).isEqualTo(50.0);
        assertThat(comparison.answerRateChangePoints()).isEqualTo(10.0);
        assertThat(comparison.avgDurationChangePct()).isEqualTo(50.0);
        assertThat(comparison.dispositionShares()).containsEntry("TRANSFERRED", 20.0).containsEntry("PROMISE_TO_PAY", 30.0);
        assertThat(comparison.dispositionShareChangePoints())
                .containsEntry("TRANSFERRED", 10.0)
                .containsEntry("PROMISE_TO_PAY", 30.0)
                .containsEntry("REFUSED", -20.0);
    }

    @Test
    void anEmptyPreviousPeriodLeavesTheChangeUndefined() {
        ReportSummary current = summary(10, 5, 30.0, List.of());
        ReportSummary previous = summary(0, 0, null, List.of());

        CallPeriodComparison comparison = CallPeriodComparison.of(current, previous);

        assertThat(comparison.totalCallsChangePct()).isNull();
        assertThat(comparison.avgDurationChangePct()).isNull();
        assertThat(comparison.previousAnswerRatePct()).isZero();
    }

    private static ReportSummary summary(long total, long answered, Double avgDuration, List<DashboardOutcome> outcomes) {
        return new ReportSummary(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"), null,
                new DashboardTotals(total, answered, avgDuration, 0), outcomes, List.of(), List.of());
    }
}
