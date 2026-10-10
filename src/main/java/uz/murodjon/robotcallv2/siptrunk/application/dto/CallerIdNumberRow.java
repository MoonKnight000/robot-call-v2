package uz.murodjon.robotcallv2.siptrunk.application.dto;

import java.time.Instant;

public record CallerIdNumberRow(
        long id,
        String phone,
        String label,
        long usageCount,
        long answerCount,
        double answerRate,
        String status,
        Instant coolingUntil,
        Instant lastUsedAt,
        Instant createdAt
) {
}
