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

    /** What a save carries: the adapter stamps the id, the company and the time. */
    public static TableConfig of(long userId, String configKey, String configValue) {
        return new TableConfig(0, 0, userId, configKey, configValue, null);
    }
}
