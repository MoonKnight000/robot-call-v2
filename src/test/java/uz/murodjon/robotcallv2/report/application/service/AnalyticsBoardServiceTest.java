package uz.murodjon.robotcallv2.report.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.report.application.dto.*;
import uz.murodjon.robotcallv2.report.application.mapper.BoardMapper;
import uz.murodjon.robotcallv2.report.application.port.output.AnalyticsBoardRepository;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoard;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsBoardWidget;
import uz.murodjon.robotcallv2.report.domain.entity.AnalyticsDataPoint;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;
import uz.murodjon.robotcallv2.report.domain.enums.WidgetType;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsBoardServiceTest {

    @Mock
    private AnalyticsBoardRepository repository;

    private BoardMapper mapper;
    private AnalyticsBoardService service;

    @BeforeEach
    void setUp() {
        mapper = new BoardMapper();
        service = new AnalyticsBoardService(repository, mapper);
    }

    @Test
    void createBoardSavesAndReturnsDetail() {
        long companyId = 1L;
        CreateBoardRequest request = new CreateBoardRequest("Collections KPI", "Key collection metrics", true);
        AnalyticsBoard saved = new AnalyticsBoard(5L, companyId, "Collections KPI", "Key collection metrics", true, List.of(), Instant.now(), Instant.now());

        when(repository.save(eq(companyId), any(AnalyticsBoard.class))).thenReturn(saved);
        when(repository.findById(companyId, 5L)).thenReturn(Optional.of(saved));

        BoardDetail detail = service.createBoard(companyId, request);

        assertThat(detail.id()).isEqualTo(5L);
        assertThat(detail.name()).isEqualTo("Collections KPI");
        assertThat(detail.isDefault()).isTrue();
    }

    @Test
    void addWidgetSavesAndReturnsRow() {
        long companyId = 1L;
        long boardId = 5L;
        AnalyticsBoard board = new AnalyticsBoard(boardId, companyId, "Board 1", null, false, List.of(), Instant.now(), Instant.now());
        AddBoardWidgetRequest req = new AddBoardWidgetRequest(
                "Call volume", WidgetType.BAR_CHART, AnalyticsMetric.CALL_COUNT,
                AnalyticsDimension.DATE, "LAST_7_DAYS", 0, 0, 6, 4
        );
        AnalyticsBoardWidget widget = new AnalyticsBoardWidget(
                100L, boardId, "Call volume", WidgetType.BAR_CHART, AnalyticsMetric.CALL_COUNT,
                AnalyticsDimension.DATE, "LAST_7_DAYS", 0, 0, 6, 4, Instant.now()
        );

        when(repository.findById(companyId, boardId)).thenReturn(Optional.of(board));
        when(repository.saveWidget(eq(companyId), eq(boardId), any(AnalyticsBoardWidget.class))).thenReturn(widget);

        BoardWidgetRow row = service.addWidget(companyId, boardId, req);

        assertThat(row.id()).isEqualTo(100L);
        assertThat(row.title()).isEqualTo("Call volume");
        assertThat(row.metric()).isEqualTo("CALL_COUNT");
    }

    @Test
    void queryAnalyticsCallsRepository() {
        long companyId = 1L;
        AnalyticsQueryRequest req = new AnalyticsQueryRequest(
                AnalyticsMetric.SUCCESS_RATE, AnalyticsDimension.DATE, null, null, null, null
        );
        AnalyticsDataPoint dp = new AnalyticsDataPoint("2026-10-09", "2026-10-09", 75.5, 100.0);
        when(repository.queryAnalytics(eq(companyId), eq(AnalyticsMetric.SUCCESS_RATE), eq(AnalyticsDimension.DATE), any(), any(), any(), any()))
                .thenReturn(List.of(dp));

        AnalyticsQueryResult result = service.queryAnalytics(companyId, req);

        assertThat(result.metric()).isEqualTo("SUCCESS_RATE");
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).value()).isEqualTo(75.5);
    }

    @Test
    void getBoardThrowsNotFoundWhenMissing() {
        when(repository.findById(1L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBoard(1L, 999L))
                .isInstanceOf(NotFoundException.class);
    }
}
