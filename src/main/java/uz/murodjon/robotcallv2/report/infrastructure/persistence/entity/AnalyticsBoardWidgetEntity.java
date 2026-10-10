package uz.murodjon.robotcallv2.report.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsDimension;
import uz.murodjon.robotcallv2.report.domain.enums.AnalyticsMetric;
import uz.murodjon.robotcallv2.report.domain.enums.WidgetType;

import java.time.Instant;

@Entity
@Table(name = "analytics_board_widget")
public class AnalyticsBoardWidgetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private AnalyticsBoardEntity board;

    @Column(name = "board_id", insertable = false, updatable = false)
    private Long boardId;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "widget_type", nullable = false, length = 32)
    private WidgetType widgetType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AnalyticsMetric metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_by", length = 32)
    private AnalyticsDimension groupBy;

    @Column(name = "time_range", nullable = false, length = 32)
    private String timeRange = "LAST_7_DAYS";

    @Column(name = "grid_row", nullable = false)
    private int gridRow;

    @Column(name = "grid_col", nullable = false)
    private int gridCol;

    @Column(name = "grid_width", nullable = false)
    private int gridWidth = 6;

    @Column(name = "grid_height", nullable = false)
    private int gridHeight = 4;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AnalyticsBoardEntity getBoard() {
        return board;
    }

    public void setBoard(AnalyticsBoardEntity board) {
        this.board = board;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public WidgetType getWidgetType() {
        return widgetType;
    }

    public void setWidgetType(WidgetType widgetType) {
        this.widgetType = widgetType;
    }

    public AnalyticsMetric getMetric() {
        return metric;
    }

    public void setMetric(AnalyticsMetric metric) {
        this.metric = metric;
    }

    public AnalyticsDimension getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(AnalyticsDimension groupBy) {
        this.groupBy = groupBy;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public int getGridRow() {
        return gridRow;
    }

    public void setGridRow(int gridRow) {
        this.gridRow = gridRow;
    }

    public int getGridCol() {
        return gridCol;
    }

    public void setGridCol(int gridCol) {
        this.gridCol = gridCol;
    }

    public int getGridWidth() {
        return gridWidth;
    }

    public void setGridWidth(int gridWidth) {
        this.gridWidth = gridWidth;
    }

    public int getGridHeight() {
        return gridHeight;
    }

    public void setGridHeight(int gridHeight) {
        this.gridHeight = gridHeight;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
