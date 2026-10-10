package uz.murodjon.robotcallv2.siptrunk.application.dto;

import java.time.Instant;
import java.util.List;

public record CallerIdPoolDetail(
        long id,
        String name,
        String strategy,
        String description,
        List<CallerIdNumberRow> numbers,
        Instant createdAt,
        Instant updatedAt
) {
}
