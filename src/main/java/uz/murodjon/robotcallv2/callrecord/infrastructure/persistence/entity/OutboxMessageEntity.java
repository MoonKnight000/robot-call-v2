package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageKind;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageStatus;

import java.time.Instant;

/**
 * JPA entity for {@code outbox_message}. Read-only from Java: rows are inserted and moved
 * between states by the native statements in {@code OutboxMessageJpaRepository}.
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxMessageKind kind;

    @Column(name = "call_attempt_id", nullable = false)
    private Long callAttemptId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxMessageStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public OutboxMessageKind getKind() {
        return kind;
    }

    public Long getCallAttemptId() {
        return callAttemptId;
    }

    public String getPayload() {
        return payload;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OutboxMessageStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
