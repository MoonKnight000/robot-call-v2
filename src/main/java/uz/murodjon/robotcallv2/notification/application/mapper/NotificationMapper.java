package uz.murodjon.robotcallv2.notification.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationEntity;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationRecipientEntity;

@Component
public class NotificationMapper {

    public Notification recipientToDomain(NotificationRecipientEntity recipient) {
        if (recipient == null || recipient.getNotification() == null) {
            return null;
        }
        NotificationEntity entity = recipient.getNotification();
        return new Notification(
                entity.getId(),
                entity.getType(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getLink(),
                recipient.getReadAt() != null,
                entity.getCreatedAt()
        );
    }
}
