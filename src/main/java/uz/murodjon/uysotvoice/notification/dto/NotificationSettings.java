package uz.murodjon.uysotvoice.notification.dto;

import java.util.List;

/** {@code GET/PUT /api/settings/notifications} (§11) response — the whole grid at once. */
public record NotificationSettings(
        List<NotificationChannel> channels,
        List<NotificationMatrixEntry> matrix
) {
}
