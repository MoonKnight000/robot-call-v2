package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.report.application.port.output.CallSourceReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.CampaignStats;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.doubleOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.enumOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.firstOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.instantOrNull;
import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.rate;

/** A campaign's and an inbound route's all-time figures. Native SQL through {@link JdbcClient}. */
@Component
public class CallSourceReportRepositoryAdapter implements CallSourceReportRepository {

    private final JdbcClient jdbc;

    public CallSourceReportRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    private record CampaignHead(String name, String status) {
    }

    private record CampaignReach(long connected, long targetsReached, long targetsAttempted) {
    }

    private record RouteTotals(long totalCalls, long answeredCalls, Double avgDurationSec, Instant lastCallAt) {
    }

    /** One read-only transaction for the six queries; the caller's, when it opened one. */
    @Override
    @Transactional(readOnly = true)
    public CampaignStats findCampaignStats(long companyId, long campaignId) {
        CampaignHead campaign = jdbc.sql(
                        "SELECT name, status FROM campaign WHERE id = :campaignId AND company_id = :companyId")
                .param("campaignId", campaignId)
                .param("companyId", companyId)
                .query((rs, n) -> new CampaignHead(rs.getString("name"), rs.getString("status")))
                .optional()
                .orElse(null);
        if (campaign == null) {
            return null;
        }

        // Every query below is scoped by company as well as campaign, so no stray row can
        // put another tenant's calls into these numbers (they once did, through the shared
        // MANUAL/INBOUND placeholder targets that V35 removed).
        Map<String, Long> targets = countBy(
                "SELECT status AS k, count(*) AS n FROM campaign_target "
                        + "WHERE campaign_id = :campaignId AND company_id = :companyId GROUP BY status",
                companyId, campaignId);
        Map<String, Long> dispositions = countBy(
                "SELECT coalesce(a.disposition, 'UNKNOWN') AS k, count(*) AS n "
                        + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "WHERE t.campaign_id = :campaignId AND a.company_id = :companyId AND a.ended_at IS NOT NULL "
                        + "GROUP BY coalesce(a.disposition, 'UNKNOWN')",
                companyId, campaignId);

        long finished = dispositions.values().stream().mapToLong(Long::longValue).sum();
        long promises = dispositions.getOrDefault(Disposition.PROMISE_TO_PAY.name(), 0L);
        long escalated = dispositions.getOrDefault(Disposition.TRANSFERRED.name(), 0L);

        Double avgDurationSec = firstOrNull(jdbc.sql(
                        "SELECT avg(a.duration_sec) AS avg_duration_sec "
                                + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                                + "WHERE t.campaign_id = :campaignId AND a.company_id = :companyId "
                                + "AND a.duration_sec IS NOT NULL")
                .param("campaignId", campaignId)
                .param("companyId", companyId)
                .query((rs, n) -> doubleOrNull(rs, "avg_duration_sec"))
                .list());

        // Telephony side: how many attempts were picked up, and how many distinct targets
        // were reached at all — the list's worth, separate from what the agent made of it.
        CampaignReach reach = jdbc.sql(
                        "SELECT count(*) FILTER (WHERE a.answered_at IS NOT NULL) AS connected, "
                                + "count(DISTINCT a.target_id) FILTER (WHERE a.answered_at IS NOT NULL) AS targets_reached, "
                                + "count(DISTINCT a.target_id) AS targets_attempted "
                                + "FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                                + "WHERE t.campaign_id = :campaignId AND a.company_id = :companyId "
                                + "AND a.ended_at IS NOT NULL")
                .param("campaignId", campaignId)
                .param("companyId", companyId)
                .query((rs, n) -> new CampaignReach(
                        rs.getLong("connected"), rs.getLong("targets_reached"), rs.getLong("targets_attempted")))
                .single();

        // On which attempt a target was first reached: attempts numbered per target in the
        // order they were placed, the first connected one counted. Numbered once, inside
        // this campaign — the correlated form re-numbered every tenant's attempts per row.
        Map<Integer, Long> connectedByAttemptNo = new LinkedHashMap<>();
        jdbc.sql("WITH numbered AS ("
                        + "  SELECT a.target_id, a.answered_at, row_number() OVER (PARTITION BY a.target_id "
                        + "         ORDER BY coalesce(a.started_at, a.created_at), a.id) AS n "
                        + "  FROM call_attempt a JOIN campaign_target t ON t.id = a.target_id "
                        + "  WHERE t.campaign_id = :campaignId AND a.company_id = :companyId AND a.ended_at IS NOT NULL"
                        + ") "
                        + "SELECT n AS attempt_no, count(*) AS targets FROM ("
                        + "  SELECT target_id, min(n) AS n FROM numbered WHERE answered_at IS NOT NULL GROUP BY target_id"
                        + ") first_connected GROUP BY n ORDER BY n")
                .param("campaignId", campaignId)
                .param("companyId", companyId)
                .query((ResultSet rs) -> {
                    connectedByAttemptNo.put(rs.getInt("attempt_no"), rs.getLong("targets"));
                });

        long connected = reach.connected();
        return new CampaignStats(
                campaignId,
                campaign.name(),
                enumOrNull(CampaignStatus.class, campaign.status()),
                targets,
                dispositions,
                finished,
                finished == 0 ? 0d : (double) promises / finished,
                avgDurationSec,
                escalated,
                connected,
                finished == 0 ? 0d : (double) connected / finished,
                reach.targetsAttempted() == 0 ? 0d : (double) reach.targetsReached() / reach.targetsAttempted(),
                connectedByAttemptNo,
                0L,
                null,
                null);
    }

    /** One read-only transaction for the four queries. */
    @Override
    @Transactional(readOnly = true)
    public InboundRouteStats findInboundRouteStats(long companyId, long inboundRouteId) {
        String didNumber = jdbc.sql(
                        "SELECT did_number FROM inbound_route WHERE id = :routeId AND company_id = :companyId")
                .param("routeId", inboundRouteId)
                .param("companyId", companyId)
                .query((rs, n) -> rs.getString("did_number"))
                .optional()
                .orElse(null);
        if (didNumber == null) {
            return null;
        }

        RouteTotals totals = jdbc.sql(
                        "SELECT count(*) AS total_calls, "
                                + "count(*) FILTER (WHERE answered_at IS NOT NULL) AS answered_calls, "
                                + "avg(duration_sec) FILTER (WHERE duration_sec IS NOT NULL) AS avg_duration_sec, "
                                + "max(started_at) AS last_call_at "
                                + "FROM call_attempt WHERE inbound_route_id = :routeId AND company_id = :companyId")
                .param("routeId", inboundRouteId)
                .param("companyId", companyId)
                .query((rs, n) -> new RouteTotals(rs.getLong("total_calls"), rs.getLong("answered_calls"),
                        doubleOrNull(rs, "avg_duration_sec"), instantOrNull(rs, "last_call_at")))
                .single();

        Map<String, Long> dispositions = new LinkedHashMap<>();
        jdbc.sql("SELECT coalesce(disposition, 'UNKNOWN') AS k, count(*) AS n FROM call_attempt "
                        + "WHERE inbound_route_id = :routeId AND company_id = :companyId AND ended_at IS NOT NULL "
                        + "GROUP BY coalesce(disposition, 'UNKNOWN')")
                .param("routeId", inboundRouteId)
                .param("companyId", companyId)
                .query((ResultSet rs) -> {
                    dispositions.put(rs.getString("k"), rs.getLong("n"));
                });

        Instant earliest = firstOrNull(jdbc.sql(
                        "SELECT min(started_at) AS earliest FROM call_attempt "
                                + "WHERE company_id = :companyId AND inbound_route_id IS NOT NULL")
                .param("companyId", companyId)
                .query((rs, n) -> instantOrNull(rs, "earliest"))
                .list());

        return new InboundRouteStats(inboundRouteId, didNumber, totals.totalCalls(), totals.answeredCalls(),
                rate(totals.answeredCalls(), totals.totalCalls()), totals.avgDurationSec(), totals.lastCallAt(),
                dispositions, earliest);
    }

    private Map<String, Long> countBy(String sql, long companyId, long campaignId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.sql(sql)
                .param("companyId", companyId)
                .param("campaignId", campaignId)
                .query((ResultSet rs) -> {
                    counts.put(rs.getString("k"), rs.getLong("n"));
                });
        return counts;
    }
}
