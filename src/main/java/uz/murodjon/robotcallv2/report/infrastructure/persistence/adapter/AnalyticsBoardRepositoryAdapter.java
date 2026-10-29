package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.report.application.port.output.AnalyticsBoardRepository;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoard;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoardWidget;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsDataPoint;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.AnalyticsBoardEntity;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.AnalyticsBoardWidgetEntity;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.mapper.AnalyticsBoardEntityMapper;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.repository.AnalyticsBoardJpaRepository;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.repository.AnalyticsBoardWidgetJpaRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter.ReportSql.toTimestamptz;

@Component
public class AnalyticsBoardRepositoryAdapter implements AnalyticsBoardRepository {

    private final AnalyticsBoardJpaRepository boardJpaRepository;
    private final AnalyticsBoardWidgetJpaRepository widgetJpaRepository;
    private final AnalyticsBoardEntityMapper boardMapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final JdbcClient jdbcClient;

    public AnalyticsBoardRepositoryAdapter(AnalyticsBoardJpaRepository boardJpaRepository,
                                           AnalyticsBoardWidgetJpaRepository widgetJpaRepository,
                                           AnalyticsBoardEntityMapper boardMapper,
                                           CompanyJpaRepository companyJpaRepository,
                                           JdbcClient jdbcClient) {
        this.boardJpaRepository = boardJpaRepository;
        this.widgetJpaRepository = widgetJpaRepository;
        this.boardMapper = boardMapper;
        this.companyJpaRepository = companyJpaRepository;
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public AnalyticsBoard save(long companyId, AnalyticsBoard board) {
        AnalyticsBoardEntity entity;
        if (board.id() > 0) {
            entity = boardJpaRepository.findByIdAndCompanyId(board.id(), companyId)
                    .orElseGet(AnalyticsBoardEntity::new);
        } else {
            entity = new AnalyticsBoardEntity();
            entity.setCompany(companyJpaRepository.getReferenceById(companyId));
            entity.setCreatedAt(Instant.now());
        }
        entity.setName(board.name());
        entity.setDescription(board.description());
        entity.setDefault(board.isDefault());
        entity.setUpdatedAt(Instant.now());

        AnalyticsBoardEntity saved = boardJpaRepository.save(entity);
        return boardMapper.toAnalyticsBoard(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalyticsBoard> findById(long companyId, long id) {
        return boardJpaRepository.findByIdAndCompanyIdWithWidgets(id, companyId)
                .map(boardMapper::toAnalyticsBoard);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsBoard> findAllByCompanyId(long companyId) {
        return boardJpaRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(boardMapper::toAnalyticsBoard)
                .toList();
    }

    @Override
    @Transactional
    public void delete(long companyId, long id) {
        boardJpaRepository.findByIdAndCompanyId(id, companyId)
                .ifPresent(boardJpaRepository::delete);
    }

    @Override
    @Transactional
    public AnalyticsBoardWidget saveWidget(long companyId, long boardId, AnalyticsBoardWidget widget) {
        AnalyticsBoardEntity board = boardJpaRepository.findByIdAndCompanyId(boardId, companyId)
                .orElse(null);
        if (board == null) {
            return null;
        }

        AnalyticsBoardWidgetEntity entity;
        if (widget.id() > 0) {
            entity = widgetJpaRepository.findByIdAndBoardId(widget.id(), boardId)
                    .orElseGet(AnalyticsBoardWidgetEntity::new);
        } else {
            entity = new AnalyticsBoardWidgetEntity();
            entity.setBoard(board);
            entity.setCreatedAt(Instant.now());
        }
        entity.setTitle(widget.title());
        entity.setWidgetType(widget.widgetType());
        entity.setMetric(widget.metric());
        entity.setGroupBy(widget.groupBy());
        entity.setTimeRange(widget.timeRange() != null ? widget.timeRange() : "LAST_7_DAYS");
        entity.setGridRow(widget.gridRow());
        entity.setGridCol(widget.gridCol());
        entity.setGridWidth(widget.gridWidth() > 0 ? widget.gridWidth() : 6);
        entity.setGridHeight(widget.gridHeight() > 0 ? widget.gridHeight() : 4);

        AnalyticsBoardWidgetEntity saved = widgetJpaRepository.save(entity);
        return boardMapper.toAnalyticsBoardWidget(saved);
    }

    @Override
    @Transactional
    public void deleteWidget(long companyId, long boardId, long widgetId) {
        boardJpaRepository.findByIdAndCompanyId(boardId, companyId).ifPresent(b ->
                widgetJpaRepository.findByIdAndBoardId(widgetId, boardId)
                        .ifPresent(widgetJpaRepository::delete));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalyticsDataPoint> queryAnalytics(long companyId, AnalyticsMetric metric,
                                                   AnalyticsDimension groupBy, Instant from,
                                                   Instant to, Long campaignId, Long agentId) {
        Instant effectiveFrom = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant effectiveTo = to != null ? to : Instant.now();

        String selectGroup = switch (groupBy != null ? groupBy : AnalyticsDimension.DATE) {
            case DATE -> "to_char(a.created_at, 'YYYY-MM-DD')";
            case AGENT -> "COALESCE(ag.name, 'No Agent')";
            case CAMPAIGN -> "COALESCE(c.name, 'Direct/Manual')";
            case DISPOSITION -> "COALESCE(a.disposition, 'UNKNOWN')";
            case LANGUAGE -> "COALESCE(a.language, 'uz-UZ')";
        };

        String metricAgg = switch (metric != null ? metric : AnalyticsMetric.CALL_COUNT) {
            case CALL_COUNT -> "count(*)";
            case SUCCESS_RATE -> "COALESCE(100.0 * count(*) FILTER (WHERE a.disposition IN ('COMPLETED', 'PROMISE_TO_PAY', 'GOAL_REACHED')) / NULLIF(count(*), 0), 0.0)";
            case AVG_DURATION_SECONDS -> "COALESCE(avg(a.duration_sec) FILTER (WHERE a.answered_at IS NOT NULL), 0.0)";
            case PTP_COUNT -> "count(*) FILTER (WHERE a.disposition = 'PROMISE_TO_PAY')";
            case TOTAL_COST_UZS -> "COALESCE(sum(b.total_uzs) FILTER (WHERE b.status = 'SETTLED'), 0)";
            case AVG_LATENCY_MS -> "COALESCE(avg(ct.avg_turn_latency_ms), 0.0)";
        };

        // Only the grouping and the aggregate vary, and both come from enum switches above;
        // every value is a bound parameter. call_billing and call_technical are one row per call.
        String sql = """
            SELECT %s AS grp_key,
                   %s AS grp_val,
                   count(*) AS call_total
            FROM call_attempt a
            LEFT JOIN campaign_target t ON t.id = a.target_id
            LEFT JOIN campaign c ON c.id = t.campaign_id
            LEFT JOIN ai_agent ag ON ag.id = a.ai_agent_id
            LEFT JOIN call_billing b ON b.call_attempt_id = a.id
            LEFT JOIN call_technical ct ON ct.call_id = a.id
            WHERE a.company_id = :companyId
              AND a.created_at >= :from
              AND a.created_at <= :to
              AND (CAST(:campaignId AS bigint) IS NULL OR t.campaign_id = CAST(:campaignId AS bigint))
              AND (CAST(:agentId AS bigint) IS NULL OR a.ai_agent_id = CAST(:agentId AS bigint))
            GROUP BY %s ORDER BY grp_key ASC LIMIT 100
            """.formatted(selectGroup, metricAgg, selectGroup);

        return jdbcClient.sql(sql)
                .param("companyId", companyId)
                .param("from", toTimestamptz(effectiveFrom))
                .param("to", toTimestamptz(effectiveTo))
                .param("campaignId", campaignId)
                .param("agentId", agentId)
                .query((rs, rowNum) -> {
                    String key = rs.getString("grp_key") != null ? rs.getString("grp_key") : "N/A";
                    return new AnalyticsDataPoint(key, key, rs.getDouble("grp_val"), rs.getDouble("call_total"));
                })
                .list();
    }
}
