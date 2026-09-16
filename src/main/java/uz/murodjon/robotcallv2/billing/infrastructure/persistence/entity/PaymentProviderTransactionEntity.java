package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;

import java.time.Instant;

@Entity
@Table(name = "payment_provider_transaction")
public class PaymentProviderTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    private PaymentMethod provider;

    @Column(name = "provider_transaction_id", nullable = false, length = 64)
    private String providerTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topup_id", nullable = false)
    private PaymentTopupEntity topup;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "topup_id", insertable = false, updatable = false)
    private Long topupId;

    @Column(name = "amount_tiyin", nullable = false)
    private Long amountTiyin;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private ProviderTransactionState state;

    @Column(name = "provider_time")
    private Instant providerTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "performed_at")
    private Instant performedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "reason")
    private Integer reason;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PaymentMethod getProvider() { return provider; }
    public void setProvider(PaymentMethod provider) { this.provider = provider; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }
    public PaymentTopupEntity getTopup() { return topup; }
    public void setTopup(PaymentTopupEntity topup) { this.topup = topup; }
    public Long getTopupId() { return topup != null ? topup.getId() : null; }
    public Long getAmountTiyin() { return amountTiyin; }
    public void setAmountTiyin(Long amountTiyin) { this.amountTiyin = amountTiyin; }
    public ProviderTransactionState getState() { return state; }
    public void setState(ProviderTransactionState state) { this.state = state; }
    public Instant getProviderTime() { return providerTime; }
    public void setProviderTime(Instant providerTime) { this.providerTime = providerTime; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPerformedAt() { return performedAt; }
    public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public Integer getReason() { return reason; }
    public void setReason(Integer reason) { this.reason = reason; }
}
