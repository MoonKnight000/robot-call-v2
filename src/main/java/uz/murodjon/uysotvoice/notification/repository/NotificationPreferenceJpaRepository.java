package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.notification.entity.NotificationPreferenceEntity;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;

import java.util.Optional;

/** Spring Data repository for {@link NotificationPreferenceEntity}. */
public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

    Optional<NotificationPreferenceEntity> findByUserIdAndType(long userId, NotificationType type);
}
