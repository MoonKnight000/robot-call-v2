package uz.murodjon.robotcallv2.alert.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.alert.application.port.output.AlertMetricRepository;
import uz.murodjon.robotcallv2.alert.domain.entity.AlertMetricValue;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** Native SQL through {@link JdbcClient} over call_attempt and the tables hanging off it. */
@Component
public class AlertMetricRepositoryAdapter implements AlertMetricRepository {

    /** Calls an AI agent ran, of the company, ended in the window; one agent's when {@code aiAgentId} is set. */
    private static final String AI_CALLS_ENDED = """
             WHERE a.company_id = :companyId AND a.ended_at >= :since AND a.ai_agent_id IS NOT NULL
               AND (CAST(:aiAgentId AS bigint) IS NULL OR a.ai_agent_id = CAST(:aiAgentId AS bigint))
            """;

    private final JdbcClient jdbc;

    public AlertMetricRepositoryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public AlertMetricValue findDispositionRate(long companyId, Long aiAgentId, Instant since,
                                                Set<Disposition> dispositions) {
        return rate("SELECT count(*) AS total, count(*) FILTER (WHERE a.disposition IN (:dispositions)) AS hits "
                + "FROM call_attempt a" + AI_CALLS_ENDED, companyId, aiAgentId, since,
                dispositions.stream().map(Enum::name).toList());
    }

    @Override
    public AlertMetricValue findNegativeSentimentRate(long companyId, Long aiAgentId, Instant since) {
        return rate("SELECT count(*) AS total, "
                + "count(*) FILTER (WHERE r.sentiment IN ('NEGATIVE', 'HOSTILE')) AS hits "
                + "FROM call_attempt a JOIN call_result r ON r.call_id = a.id" + AI_CALLS_ENDED,
                companyId, aiAgentId, since, null);
    }

    @Override
    public AlertMetricValue findAverageTurnLatency(long companyId, Long aiAgentId, Instant since) {
        return jdbc.sql("SELECT count(t.avg_turn_latency_ms) AS total, avg(t.avg_turn_latency_ms) AS average "
                        + "FROM call_attempt a JOIN call_technical t ON t.call_id = a.id" + AI_CALLS_ENDED)
                .param("companyId", companyId)
                .param("aiAgentId", aiAgentId)
                .param("since", toTimestamptz(since))
                .query((rs, n) -> {
                    long total = rs.getLong("total");
                    return new AlertMetricValue(total == 0 ? null : rs.getDouble("average"), total);
                })
                .single();
    }

    /** Requests made on calls only — a run of the panel's Test button is not a production error. */
    @Override
    public AlertMetricValue countToolErrors(long companyId, Long aiAgentId, Instant since) {
        return jdbc.sql("""
                        SELECT count(*) AS total, count(*) FILTER (WHERE e.status <> 'OK') AS hits
                        FROM tool_execution e JOIN call_attempt a ON a.id = e.call_attempt_id
                        WHERE e.company_id = :companyId AND e.created_at >= :since
                          AND (CAST(:aiAgentId AS bigint) IS NULL OR a.ai_agent_id = CAST(:aiAgentId AS bigint))
                        """)
                .param("companyId", companyId)
                .param("aiAgentId", aiAgentId)
                .param("since", toTimestamptz(since))
                .query((rs, n) -> new AlertMetricValue((double) rs.getLong("hits"), rs.getLong("total")))
                .single();
    }

    @Override
    public AlertMetricValue countEndedCalls(long companyId, Long aiAgentId, Instant since) {
        return jdbc.sql("SELECT count(*) AS total FROM call_attempt a" + AI_CALLS_ENDED)
                .param("companyId", companyId)
                .param("aiAgentId", aiAgentId)
                .param("since", toTimestamptz(since))
                .query((rs, n) -> {
                    long total = rs.getLong("total");
                    return new AlertMetricValue((double) total, total);
                })
                .single();
    }

    /** {@code hits} as a percentage of {@code total}; no value when there is no total. */
    private AlertMetricValue rate(String sql, long companyId, Long aiAgentId, Instant since,
                                  List<String> dispositions) {
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("companyId", companyId)
                .param("aiAgentId", aiAgentId)
                .param("since", toTimestamptz(since));
        if (dispositions != null) {
            statement = statement.param("dispositions", dispositions);
        }
        return statement.query((rs, n) -> {
            long total = rs.getLong("total");
            return new AlertMetricValue(total == 0 ? null : rs.getLong("hits") * 100.0 / total, total);
        }).single();
    }

    private static OffsetDateTime toTimestamptz(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
