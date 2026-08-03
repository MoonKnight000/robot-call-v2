package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.notification.entity.NotificationRecipientEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link NotificationRecipientEntity}. */
public interface NotificationRecipientJpaRepository extends JpaRepository<NotificationRecipientEntity, Long> {

    @Query("SELECT r FROM NotificationRecipientEntity r WHERE r.userId = :userId ORDER BY r.id DESC")
    List<NotificationRecipientEntity> findByUserId(@Param("userId") long userId);

    Optional<NotificationRecipientEntity> findByNotificationIdAndUserId(long notificationId, long userId);

    long countByUserIdAndReadAtIsNull(long userId);

    @Modifying @Transactional
    @Query("UPDATE NotificationRecipientEntity r SET r.readAt = :at "
            + "WHERE r.notificationId = :notificationId AND r.userId = :userId")
    void markRead(@Param("notificationId") long notificationId, @Param("userId") long userId,
                  @Param("at") Instant at);
}
