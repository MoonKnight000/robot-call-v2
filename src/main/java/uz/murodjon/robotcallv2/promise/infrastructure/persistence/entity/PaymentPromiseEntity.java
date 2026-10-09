package uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.promise.domain.enums.PaymentPromiseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Read-only through JPA: every write is a native statement in {@code PaymentPromiseJpaRepository}. */
@Entity
@Table(name = "payment_promise")
public class PaymentPromiseEntity {

    @Id
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "call_attempt_id", nullable = false)
    private Long callAttemptId;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "promised_date", nullable = false)
    private LocalDate promisedDate;

    @Column(name = "promised_amount")
    private BigDecimal promisedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentPromiseStatus status;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getCallAttemptId() { return callAttemptId; }
    public Long getTargetId() { return targetId; }
    public String getPhone() { return phone; }
    public LocalDate getPromisedDate() { return promisedDate; }
    public BigDecimal getPromisedAmount() { return promisedAmount; }
    public PaymentPromiseStatus getStatus() { return status; }
    public Instant getReminderSentAt() { return reminderSentAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
