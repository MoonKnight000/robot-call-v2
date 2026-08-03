package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.notification.enums.NotificationChannelType;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;

/** One cell of the personal channel x event matrix in {@code GET/PUT /api/profile/notifications} (§15). */
public record PersonalNotificationMatrixEntry(
        @NotNull NotificationType type,
        @NotNull NotificationChannelType channel,
        boolean enabled
) {
}
