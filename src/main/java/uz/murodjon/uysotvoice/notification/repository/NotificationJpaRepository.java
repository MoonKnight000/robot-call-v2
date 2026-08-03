package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.notification.entity.NotificationEntity;

/** Spring Data repository for {@link NotificationEntity}. */
public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, Long> {
}
