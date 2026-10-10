package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoard;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoardWidget;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsDataPoint;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Output persistence port for custom analytics boards and aggregation queries.
 */
public interface AnalyticsBoardRepository {

    AnalyticsBoard save(long companyId, AnalyticsBoard board);

    Optional<AnalyticsBoard> findById(long companyId, long id);

    List<AnalyticsBoard> findAllByCompanyId(long companyId);

    void delete(long companyId, long id);

    AnalyticsBoardWidget saveWidget(long companyId, long boardId, AnalyticsBoardWidget widget);

    void deleteWidget(long companyId, long boardId, long widgetId);

    List<AnalyticsDataPoint> queryAnalytics(long companyId, AnalyticsMetric metric, AnalyticsDimension groupBy,
                                           Instant from, Instant to, Long campaignId, Long agentId);
}
