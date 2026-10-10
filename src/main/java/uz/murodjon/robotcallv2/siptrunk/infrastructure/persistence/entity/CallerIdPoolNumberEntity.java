package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.CallerIdNumberStatus;

import java.time.Instant;

@Entity
@Table(name = "caller_id_pool_number")
public class CallerIdPoolNumberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private CallerIdPoolEntity pool;

    @Column(name = "pool_id", insertable = false, updatable = false)
    private Long poolId;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(length = 100)
    private String label;

    @Column(name = "usage_count", nullable = false)
    private long usageCount;

    @Column(name = "answer_count", nullable = false)
    private long answerCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CallerIdNumberStatus status = CallerIdNumberStatus.ACTIVE;

    @Column(name = "cooling_until")
    private Instant coolingUntil;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CallerIdPoolEntity getPool() {
        return pool;
    }

    public void setPool(CallerIdPoolEntity pool) {
        this.pool = pool;
    }

    public Long getPoolId() {
        return poolId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(long usageCount) {
        this.usageCount = usageCount;
    }

    public long getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(long answerCount) {
        this.answerCount = answerCount;
    }

    public CallerIdNumberStatus getStatus() {
        return status;
    }

    public void setStatus(CallerIdNumberStatus status) {
        this.status = status;
    }

    public Instant getCoolingUntil() {
        return coolingUntil;
    }

    public void setCoolingUntil(Instant coolingUntil) {
        this.coolingUntil = coolingUntil;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
