package uz.murodjon.uysotvoice.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code PUT /api/settings/notifications} (§11) body — replaces the whole grid at once. */
public record UpdateNotificationSettingsRequest(
        @NotNull @Valid List<NotificationChannel> channels,
        @NotNull @Valid List<NotificationMatrixEntry> matrix
) {
}
