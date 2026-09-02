package uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationPreferenceEntity;

import java.util.Optional;

@Repository
public interface NotificationPreferenceJpaRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

    Optional<NotificationPreferenceEntity> findByUserIdAndType(long userId, NotificationType type);
}
