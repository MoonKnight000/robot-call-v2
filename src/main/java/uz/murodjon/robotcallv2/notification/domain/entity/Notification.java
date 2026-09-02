package uz.murodjon.robotcallv2.notification.domain.entity;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Instant;

/**
 * Domain model for an in-app notification (UI-DESIGN §9 topbar bell, §0.8).
 */
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
