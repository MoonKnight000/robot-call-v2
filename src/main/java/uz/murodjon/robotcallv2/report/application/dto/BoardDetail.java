package uz.murodjon.robotcallv2.report.application.dto;

import java.time.Instant;
import java.util.List;

public record BoardDetail(
        long id,
        String name,
        String description,
        boolean isDefault,
        List<BoardWidgetRow> widgets,
        Instant createdAt,
        Instant updatedAt
) {
}
