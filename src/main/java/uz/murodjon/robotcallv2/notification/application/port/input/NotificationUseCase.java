package uz.murodjon.robotcallv2.notification.application.port.input;

import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.util.List;

public interface NotificationUseCase {

    void notify(long companyId, NotificationType type, String title, String message, String link);

    List<Notification> list();

    void markRead(long notificationId);

    void setPreference(NotificationType type, boolean enabled);
}
