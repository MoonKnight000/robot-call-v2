package uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationEntity;

@Repository
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {
}
