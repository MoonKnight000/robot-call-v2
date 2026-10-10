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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            case TOTAL_COST_UZS -> "COALESCE(sum(a.duration_sec) * 10, 0)";
            case AVG_LATENCY_MS -> "COALESCE(avg(a.duration_sec) * 100, 0.0)";
        };

        StringBuilder sql = new StringBuilder("""
            SELECT %s AS grp_key,
                   %s AS grp_val,
                   count(*) AS call_total
            FROM call_attempt a
            LEFT JOIN campaign c ON a.campaign_id = c.id
            LEFT JOIN ai_agent ag ON c.ai_agent_id = ag.id
            WHERE a.company_id = :companyId
              AND a.created_at >= :from
              AND a.created_at <= :to
            """.formatted(selectGroup, metricAgg));

        if (campaignId != null) {
            sql.append(" AND a.campaign_id = :campaignId ");
        }
        if (agentId != null) {
            sql.append(" AND (c.ai_agent_id = :agentId) ");
        }

        sql.append(" GROUP BY %s ORDER BY grp_key ASC LIMIT 100".formatted(selectGroup));

        var client = jdbcClient.sql(sql.toString())
                .param("companyId", companyId)
                .param("from", effectiveFrom)
                .param("to", effectiveTo);

        if (campaignId != null) {
            client.param("campaignId", campaignId);
        }
        if (agentId != null) {
            client.param("agentId", agentId);
        }

        List<AnalyticsDataPoint> points = new ArrayList<>();
        client.query((rs, rowNum) -> {
            String key = rs.getString("grp_key");
            double val = rs.getDouble("grp_val");
            double total = rs.getDouble("call_total");
            points.add(new AnalyticsDataPoint(key != null ? key : "N/A", key != null ? key : "N/A", val, total));
            return null;
        }).list();

        return points;
    }
}
