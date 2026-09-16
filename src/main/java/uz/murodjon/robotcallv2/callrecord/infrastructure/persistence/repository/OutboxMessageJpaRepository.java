package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageStatus;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.OutboxMessageEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Spring Data repository for {@link OutboxMessageEntity}. */
@Repository
public interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageEntity, Long> {

    /** The new row's id — empty when a row with that idempotency key already exists. */
    @Transactional
    @Query(value = "INSERT INTO outbox_message (company_id, kind, call_attempt_id, payload, idempotency_key) "
            + "VALUES (:companyId, :kind, :callAttemptId, CAST(:payload AS jsonb), :idempotencyKey) "
            + "ON CONFLICT (idempotency_key) DO NOTHING RETURNING id", nativeQuery = true)
    List<Long> insertIgnoringConflict(@Param("companyId") long companyId, @Param("kind") String kind,
                                      @Param("callAttemptId") long callAttemptId, @Param("payload") String payload,
                                      @Param("idempotencyKey") String idempotencyKey);

    @Transactional
    @Query(value = "UPDATE outbox_message SET attempts = attempts + 1, next_attempt_at = :leaseUntil "
            + "WHERE id IN ("
            + "  SELECT id FROM outbox_message"
            + "  WHERE status = 'PENDING' AND next_attempt_at <= :now"
            + "  ORDER BY next_attempt_at"
            + "  LIMIT :limit FOR UPDATE SKIP LOCKED"
            + ") RETURNING *", nativeQuery = true)
    List<OutboxMessageEntity> claimDue(@Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil,
                                       @Param("limit") int limit);

    /**
     * {@code attempts = 0} rather than a due time: the row was stored a moment ago with the
     * database's clock, and comparing it with this instance's clock could miss it.
     */
    @Transactional
    @Query(value = "UPDATE outbox_message SET attempts = attempts + 1, next_attempt_at = :leaseUntil "
            + "WHERE id = :id AND status = 'PENDING' AND attempts = 0 RETURNING *", nativeQuery = true)
    List<OutboxMessageEntity> claimFresh(@Param("id") long id, @Param("leaseUntil") Instant leaseUntil);

    /** Fenced on the attempt count the claim produced; 0 when a newer claim owns the row. */
    @Modifying
    @Transactional
    @Query("UPDATE OutboxMessageEntity m SET m.status = :status, m.payload = :payload, "
            + "m.nextAttemptAt = :nextAttemptAt, m.lastError = :lastError, m.sentAt = :sentAt "
            + "WHERE m.id = :id AND m.attempts = :attempts "
            + "AND m.status = uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageStatus.PENDING")
    int updateClaimed(@Param("id") long id, @Param("attempts") int attempts,
                      @Param("status") OutboxMessageStatus status, @Param("payload") String payload,
                      @Param("nextAttemptAt") Instant nextAttemptAt, @Param("lastError") String lastError,
                      @Param("sentAt") Instant sentAt);

    /** At most {@code batchSize} SENT or DEAD rows, across every company, created before {@code cutoff}. */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM outbox_message WHERE id IN (SELECT id FROM outbox_message "
            + "WHERE status IN ('SENT', 'DEAD') AND created_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteFinishedCreatedBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    /** The company's PENDING/DEAD rows about calls to {@code phones}, emptied; PENDING ones closed as DEAD. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE outbox_message SET payload = CAST('{}' AS jsonb), status = 'DEAD', "
            + "last_error = CASE WHEN status = 'PENDING' THEN :reason ELSE last_error END "
            + "WHERE company_id = :companyId AND status IN ('PENDING', 'DEAD') AND call_attempt_id IN ("
            + "  SELECT id FROM call_attempt WHERE company_id = :companyId AND phone IN (:phones))",
            nativeQuery = true)
    int erasePayloadsByPhones(@Param("companyId") long companyId, @Param("phones") Collection<String> phones,
                              @Param("reason") String reason);
}
