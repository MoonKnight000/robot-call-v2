package uz.murodjon.robotcallv2.notification.domain.entity;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

/**
 * Domain model for a cell of the notification channel x event matrix (§11 settings).
 */
public record NotificationMatrixEntry(
        @NotNull NotificationType type,
        @NotNull NotificationChannelType channel,
        boolean enabled
) {
}
