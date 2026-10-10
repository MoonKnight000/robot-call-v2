package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;
import java.util.List;

/**
 * A user-defined analytics dashboard board.
 */
public record AnalyticsBoard(
        long id,
        long companyId,
        String name,
        String description,
        boolean isDefault,
        List<AnalyticsBoardWidget> widgets,
        Instant createdAt,
        Instant updatedAt
) {

    public AnalyticsBoard {
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }
}
