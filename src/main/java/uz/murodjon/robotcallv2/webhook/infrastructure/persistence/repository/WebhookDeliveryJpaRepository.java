package uz.murodjon.robotcallv2.webhook.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.webhook.domain.enums.WebhookDeliveryStatus;
import uz.murodjon.robotcallv2.webhook.infrastructure.persistence.entity.WebhookDeliveryEntity;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryJpaRepository extends JpaRepository<WebhookDeliveryEntity, Long> {

    List<WebhookDeliveryEntity> findByStatusAndNextAttemptAtLessThanEqualOrderById(
            WebhookDeliveryStatus status, Instant now, Pageable page);

    @Modifying
    @Transactional
    @Query("UPDATE WebhookDeliveryEntity d SET d.nextAttemptAt = :lease "
            + "WHERE d.id = :id AND d.status = :status AND d.nextAttemptAt <= :now")
    int claim(@Param("id") long id, @Param("status") WebhookDeliveryStatus status,
              @Param("now") Instant now, @Param("lease") Instant lease);

    @Modifying
    @Transactional
    @Query("UPDATE WebhookDeliveryEntity d SET d.status = :status, d.attempts = :attempts, "
            + "d.nextAttemptAt = :nextAttemptAt, d.lastError = :error, d.deliveredAt = :deliveredAt WHERE d.id = :id")
    void updateState(@Param("id") long id, @Param("status") WebhookDeliveryStatus status,
                     @Param("attempts") int attempts, @Param("nextAttemptAt") Instant nextAttemptAt,
                     @Param("error") String error, @Param("deliveredAt") Instant deliveredAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM WebhookDeliveryEntity d WHERE d.status = :status AND d.deliveredAt < :cutoff")
    int deleteByStatusBefore(@Param("status") WebhookDeliveryStatus status, @Param("cutoff") Instant cutoff);
}
