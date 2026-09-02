package uz.murodjon.robotcallv2.profile.domain.entity;

import java.time.Instant;

public record TableConfig(
        long id,
        long companyId,
        long userId,
        String configKey,
        String configValue,
        Instant updatedAt
) {
}
