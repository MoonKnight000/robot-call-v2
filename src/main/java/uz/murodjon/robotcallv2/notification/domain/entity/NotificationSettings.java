package uz.murodjon.robotcallv2.notification.domain.entity;

import java.util.List;

/**
 * Domain model for company notification settings aggregation (§11 settings).
 */
public record NotificationSettings(
        List<NotificationChannel> channels,
        List<NotificationMatrixEntry> matrix
) {
}
