package uz.murodjon.robotcallv2.profile.domain.entity;

import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

public record PersonalNotificationMatrixEntry(
        @NotNull NotificationType type,
        @NotNull NotificationChannelType channel,
        boolean enabled
) {
}
