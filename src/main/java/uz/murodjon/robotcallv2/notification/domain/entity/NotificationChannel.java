package uz.murodjon.robotcallv2.notification.domain.entity;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;

/**
 * Domain model for a notification channel configuration (§11 settings).
 */
public record NotificationChannel(
        @NotNull NotificationChannelType channel,
        String target,
        boolean enabled
) {
}
