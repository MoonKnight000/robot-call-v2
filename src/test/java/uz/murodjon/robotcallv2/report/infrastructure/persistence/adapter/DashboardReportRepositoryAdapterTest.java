package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardFilter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** The dashboard's shared WHERE and its disposition sets — no database. */
class DashboardReportRepositoryAdapterTest {

    private static final String HEAD = "SELECT 1 FROM call_attempt a";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-08T00:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    @Test
    void successIsAPromiseToPayAReachedGoalOrACompletedCall() {
        assertThat(DashboardReportRepositoryAdapter.SUCCESS_DISPOSITIONS)
                .containsExactlyInAnyOrder("PROMISE_TO_PAY", "GOAL_REACHED", "COMPLETED");
    }

    @Test
    void failedIsANetworkOrCarrierFailure() {
        assertThat(DashboardReportRepositoryAdapter.FAILED_DISPOSITIONS)
                .containsExactlyInAnyOrder("FAILED", "CARRIER_REJECTED");
    }

    @Test
    void theChartsErrorSeriesCountsEveryFailedDisposition() {
        SqlStatement statement = DashboardReportRepositoryAdapter.bindBucketParams(
                DashboardReportRepositoryAdapter.buildDashboardQuery(HEAD, 7L,
                        new DashboardFilter(FROM, TO, null, null, ZONE)),
                "day", ZONE);

        assertThat(statement.params())
                .containsEntry("failedDispositions", DashboardReportRepositoryAdapter.FAILED_DISPOSITIONS)
                .containsEntry("zone", "Asia/Tashkent")
                .doesNotContainKey("failed");
    }

    @Test
    void averageDurationIsOverAnsweredCallsOnly() {
        assertThat(DashboardReportRepositoryAdapter.AVG_ANSWERED_DURATION)
                .isEqualTo("avg(a.duration_sec) FILTER (WHERE a.answered_at IS NOT NULL)");
    }

    @Test
    void withoutNarrowingOnlyTheCompanyAndThePeriodBound() {
        SqlStatement statement = DashboardReportRepositoryAdapter.buildDashboardQuery(HEAD, 7L,
                new DashboardFilter(FROM, TO, null, null, ZONE));

        assertThat(statement.sql()).isEqualTo(HEAD
                + " WHERE a.company_id = :companyId AND a.started_at >= :from AND a.started_at < :to");
        assertThat(statement.params())
                .containsOnlyKeys("companyId", "from", "to")
                .containsEntry("from", OffsetDateTime.ofInstant(FROM, ZoneOffset.UTC))
                .containsEntry("to", OffsetDateTime.ofInstant(TO, ZoneOffset.UTC));
    }

    @Test
    void scenarioIsAcceptedFromTheCampaignAgentOrTheInboundRouteAgent() {
        SqlStatement statement = DashboardReportRepositoryAdapter.buildDashboardQuery(HEAD, 7L,
                new DashboardFilter(FROM, TO, 3L, 5L, ZONE));

        assertThat(statement.sql()).endsWith(" AND t.campaign_id = :campaignId"
                + " AND (ag.scenario_id = :scenarioId OR ir_ag.scenario_id = :scenarioId)");
        assertThat(statement.params())
                .containsEntry("campaignId", 3L)
                .containsEntry("scenarioId", 5L);
    }
}
