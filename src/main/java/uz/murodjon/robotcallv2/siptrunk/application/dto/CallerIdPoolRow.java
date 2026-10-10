package uz.murodjon.robotcallv2.siptrunk.application.dto;

import java.time.Instant;

public record CallerIdPoolRow(
        long id,
        String name,
        String strategy,
        String description,
        int totalNumbers,
        int activeNumbers,
        Instant createdAt,
        Instant updatedAt
) {
}
