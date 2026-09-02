package uz.murodjon.robotcallv2.notification.application.port.output;

import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.util.List;

public interface NotificationRepository {

    void notify(long companyId, NotificationType type, String title, String message, String link);

    List<Notification> listForUser(long userId);

    long unreadCount(long userId);

    void markRead(long userId, long notificationId);

    void setPreference(long userId, NotificationType type, boolean enabled);
}
