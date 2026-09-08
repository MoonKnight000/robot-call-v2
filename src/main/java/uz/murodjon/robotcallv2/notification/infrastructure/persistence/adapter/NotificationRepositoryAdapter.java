package uz.murodjon.robotcallv2.notification.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.notification.application.mapper.NotificationMapper;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.Notification;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationEntity;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationPreferenceEntity;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationRecipientEntity;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository.NotificationJpaRepository;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository.NotificationPreferenceJpaRepository;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository.NotificationRecipientJpaRepository;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository notificationJpaRepository;
    private final NotificationRecipientJpaRepository notificationRecipientJpaRepository;
    private final NotificationPreferenceJpaRepository notificationPreferenceJpaRepository;
    private final UserRepository userRepository;
    private final NotificationMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final UserJpaRepository userJpaRepository;

    public NotificationRepositoryAdapter(NotificationJpaRepository notificationJpaRepository,
                                         NotificationRecipientJpaRepository notificationRecipientJpaRepository,
                                         NotificationPreferenceJpaRepository notificationPreferenceJpaRepository,
                                         UserRepository userRepository,
                                         NotificationMapper mapper,
                                         CompanyJpaRepository companyJpaRepository,
                                         UserJpaRepository userJpaRepository) {
        this.notificationJpaRepository = notificationJpaRepository;
        this.notificationRecipientJpaRepository = notificationRecipientJpaRepository;
        this.notificationPreferenceJpaRepository = notificationPreferenceJpaRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public void notify(long companyId, NotificationType type, String title, String message, String link) {
        NotificationEntity entity = new NotificationEntity();
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setType(type);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setLink(link);
        entity.setCreatedAt(Instant.now());
        NotificationEntity saved = notificationJpaRepository.save(entity);

        for (User user : userRepository.findActiveByCompany(companyId)) {
            if (isEnabled(user.id(), type)) {
                NotificationRecipientEntity recipient = new NotificationRecipientEntity();
                recipient.setNotification(saved);
                recipient.setUser(userJpaRepository.getReferenceById(user.id()));
                notificationRecipientJpaRepository.save(recipient);
            }
        }
    }

    @Override
    public List<Notification> listForUser(long userId) {
        return notificationRecipientJpaRepository.findByUserId(userId).stream()
                .map(mapper::recipientToDomain)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public long unreadCount(long userId) {
        return notificationRecipientJpaRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    public void markRead(long userId, long notificationId) {
        notificationRecipientJpaRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresent(r -> notificationRecipientJpaRepository.markRead(notificationId, userId, Instant.now()));
    }

    @Override
    public void setPreference(long userId, NotificationType type, boolean enabled) {
        NotificationPreferenceEntity entity = notificationPreferenceJpaRepository.findByUserIdAndType(userId, type)
                .orElseGet(NotificationPreferenceEntity::new);
        entity.setUser(userJpaRepository.getReferenceById(userId));
        entity.setType(type);
        entity.setEnabled(enabled);
        notificationPreferenceJpaRepository.save(entity);
    }

    private boolean isEnabled(long userId, NotificationType type) {
        return notificationPreferenceJpaRepository.findByUserIdAndType(userId, type).map(NotificationPreferenceEntity::isEnabled)
                .orElse(true);
    }
}
