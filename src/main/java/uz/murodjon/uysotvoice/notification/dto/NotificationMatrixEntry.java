package uz.murodjon.uysotvoice.notification.dto;

import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.notification.enums.NotificationChannelType;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;

/** One cell of the channel x event matrix in {@code GET/PUT /api/settings/notifications} (§11). */
public record NotificationMatrixEntry(
        @NotNull NotificationType type,
        @NotNull NotificationChannelType channel,
        boolean enabled
) {
}
