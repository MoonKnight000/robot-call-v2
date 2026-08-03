package uz.murodjon.uysotvoice.notification.dto;

import uz.murodjon.uysotvoice.notification.enums.NotificationType;

import java.time.Instant;

/** Row for {@code GET /api/notifications} (UI-DESIGN §9 topbar bell, §0.8). */
public record Notification(
        long id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt
) {
}
