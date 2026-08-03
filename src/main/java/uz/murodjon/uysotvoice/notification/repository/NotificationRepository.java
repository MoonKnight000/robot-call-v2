package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.notification.dto.Notification;
import uz.murodjon.uysotvoice.notification.entity.NotificationEntity;
import uz.murodjon.uysotvoice.notification.entity.NotificationPreferenceEntity;
import uz.murodjon.uysotvoice.notification.entity.NotificationRecipientEntity;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.user.dto.User;
import uz.murodjon.uysotvoice.user.repository.UserRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * DAO for the {@code notification}/{@code notification_recipient}/{@code
 * notification_preference} tables (UI-DESIGN §0.8/§8.2/§9). {@link #notify} is the one
 * write path every producer (transfer-to-operator, the success-rate alert) calls through
 * {@code NotificationService} — it both records the event and fans it out.
 */
@Repository
public class NotificationRepository {

    private final NotificationJpaRepository notifications;
    private final NotificationRecipientJpaRepository recipients;
    private final NotificationPreferenceJpaRepository preferences;
    private final UserRepository users;

    public NotificationRepository(NotificationJpaRepository notifications,
                                   NotificationRecipientJpaRepository recipients,
                                   NotificationPreferenceJpaRepository preferences,
                                   UserRepository users) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.preferences = preferences;
        this.users = users;
    }

    /** Writes the event, then fans it out to every active user of {@code companyId} who has not opted out. */
    public void notify(long companyId, NotificationType type, String title, String message, String link) {
        NotificationEntity entity = new NotificationEntity();
        entity.setCompanyId(companyId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setLink(link);
        entity.setCreatedAt(Instant.now());
        long notificationId = notifications.save(entity).getId();

        for (User user : users.findActiveByCompany(companyId)) {
            if (isEnabled(user.id(), type)) {
                NotificationRecipientEntity recipient = new NotificationRecipientEntity();
                recipient.setNotificationId(notificationId);
                recipient.setUserId(user.id());
                recipients.save(recipient);
            }
        }
    }

    /** Most recent first, newest 50 — a topbar bell popover, not a paginated archive. */
    public List<Notification> listForUser(long userId) {
        List<NotificationRecipientEntity> mine = recipients.findByUserId(userId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, NotificationEntity> byId = new HashMap<>();
        notifications.findAllById(mine.stream().map(NotificationRecipientEntity::getNotificationId).toList())
                .forEach(n -> byId.put(n.getId(), n));
        return mine.stream()
                .map(r -> {
                    NotificationEntity n = byId.get(r.getNotificationId());
                    return n == null ? null : new Notification(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                            n.getLink(), r.getReadAt() != null, n.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public long unreadCount(long userId) {
        return recipients.countByUserIdAndReadAtIsNull(userId);
    }

    public void markRead(long userId, long notificationId) {
        recipients.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresent(r -> recipients.markRead(notificationId, userId, Instant.now()));
    }

    public void setPreference(long userId, NotificationType type, boolean enabled) {
        NotificationPreferenceEntity entity = preferences.findByUserIdAndType(userId, type)
                .orElseGet(NotificationPreferenceEntity::new);
        entity.setUserId(userId);
        entity.setType(type);
        entity.setEnabled(enabled);
        preferences.save(entity);
    }

    private boolean isEnabled(long userId, NotificationType type) {
        return preferences.findByUserIdAndType(userId, type).map(NotificationPreferenceEntity::isEnabled)
                .orElse(true);
    }
}
