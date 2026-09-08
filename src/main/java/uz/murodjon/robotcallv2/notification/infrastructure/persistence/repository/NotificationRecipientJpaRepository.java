package uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationRecipientEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRecipientJpaRepository extends JpaRepository<NotificationRecipientEntity, Long> {

    @Query("SELECT r FROM NotificationRecipientEntity r JOIN FETCH r.notification WHERE r.user.id = :userId ORDER BY r.id DESC")
    List<NotificationRecipientEntity> findByUserId(@Param("userId") long userId);

    @Query("SELECT r FROM NotificationRecipientEntity r WHERE r.notification.id = :notificationId AND r.user.id = :userId")
    Optional<NotificationRecipientEntity> findByNotificationIdAndUserId(@Param("notificationId") long notificationId, @Param("userId") long userId);

    long countByUserIdAndReadAtIsNull(long userId);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationRecipientEntity r SET r.readAt = :at "
            + "WHERE r.notification.id = :notificationId AND r.user.id = :userId")
    void markRead(@Param("notificationId") long notificationId, @Param("userId") long userId,
                  @Param("at") Instant at);
}
