package uz.murodjon.robotcallv2.report.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.report.application.dto.BoardDetail;
import uz.murodjon.robotcallv2.report.application.dto.BoardRow;
import uz.murodjon.robotcallv2.report.application.dto.BoardWidgetRow;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoard;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoardWidget;

import java.util.List;

@Component
public class BoardMapper {

    public BoardRow toRow(AnalyticsBoard board) {
        return new BoardRow(
                board.id(),
                board.name(),
                board.description(),
                board.isDefault(),
                board.widgets().size(),
                board.createdAt(),
                board.updatedAt()
        );
    }

    public BoardDetail toDetail(AnalyticsBoard board) {
        List<BoardWidgetRow> widgets = board.widgets().stream()
                .map(this::toWidgetRow)
                .toList();
        return new BoardDetail(
                board.id(),
                board.name(),
                board.description(),
                board.isDefault(),
                widgets,
                board.createdAt(),
                board.updatedAt()
        );
    }

    public BoardWidgetRow toWidgetRow(AnalyticsBoardWidget widget) {
        return new BoardWidgetRow(
                widget.id(),
                widget.title(),
                widget.widgetType().name(),
                widget.metric().name(),
                widget.groupBy() != null ? widget.groupBy().name() : null,
                widget.timeRange(),
                widget.gridRow(),
                widget.gridCol(),
                widget.gridWidth(),
                widget.gridHeight(),
                widget.createdAt()
        );
    }
}
