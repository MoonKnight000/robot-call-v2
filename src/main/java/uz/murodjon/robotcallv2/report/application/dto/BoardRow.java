package uz.murodjon.robotcallv2.report.application.dto;

import java.time.Instant;

public record BoardRow(
        long id,
        String name,
        String description,
        boolean isDefault,
        int widgetCount,
        Instant createdAt,
        Instant updatedAt
) {
}
