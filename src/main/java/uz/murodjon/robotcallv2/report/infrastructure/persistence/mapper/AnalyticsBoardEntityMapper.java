package uz.murodjon.robotcallv2.report.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoard;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoardWidget;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.AnalyticsBoardEntity;
import uz.murodjon.robotcallv2.report.infrastructure.persistence.entity.AnalyticsBoardWidgetEntity;

import java.util.List;

@Component
public class AnalyticsBoardEntityMapper {

    public AnalyticsBoard toAnalyticsBoard(AnalyticsBoardEntity entity) {
        if (entity == null) {
            return null;
        }
        List<AnalyticsBoardWidget> widgets = entity.getWidgets() != null
                ? entity.getWidgets().stream().map(this::toAnalyticsBoardWidget).toList()
                : List.of();
        return new AnalyticsBoard(
                entity.getId(),
                entity.getCompanyId() != null ? entity.getCompanyId() : 0L,
                entity.getName(),
                entity.getDescription(),
                entity.isDefault(),
                widgets,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public AnalyticsBoardWidget toAnalyticsBoardWidget(AnalyticsBoardWidgetEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AnalyticsBoardWidget(
                entity.getId(),
                entity.getBoardId() != null ? entity.getBoardId() : 0L,
                entity.getTitle(),
                entity.getWidgetType(),
                entity.getMetric(),
                entity.getGroupBy(),
                entity.getTimeRange(),
                entity.getGridRow(),
                entity.getGridCol(),
                entity.getGridWidth(),
                entity.getGridHeight(),
                entity.getCreatedAt()
        );
    }
}
