package uz.murodjon.robotcallv2.notification.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationMatrixEntry;

import java.util.List;

/** PUT /api/settings/notifications (§11) body — replaces the whole grid at once. */
public record UpdateNotificationSettingsRequest(
        @NotNull @Valid List<NotificationChannel> channels,
        @NotNull @Valid List<NotificationMatrixEntry> matrix
) {
}
