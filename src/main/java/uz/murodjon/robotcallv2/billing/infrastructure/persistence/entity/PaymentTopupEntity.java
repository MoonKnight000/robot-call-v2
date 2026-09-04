package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;

import java.time.Instant;

@Entity
@Table(name = "payment_topup")
public class PaymentTopupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 64)
    private String paymentId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "amount_uzs", nullable = false)
    private Long amountUzs;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TopupStatus status;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public PaymentTopupEntity() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public PaymentTopup toDomain() {
        return new PaymentTopup(
                id,
                paymentId,
                companyId,
                userId,
                amountUzs != null ? amountUzs : 0L,
                paymentMethod,
                status != null ? status : TopupStatus.PENDING,
                checkoutUrl,
                createdAt,
                paidAt
        );
    }

    public static PaymentTopupEntity fromDomain(PaymentTopup domain) {
        PaymentTopupEntity entity = new PaymentTopupEntity();
        entity.id = domain.id();
        entity.paymentId = domain.paymentId();
        entity.companyId = domain.companyId();
        entity.userId = domain.userId();
        entity.amountUzs = domain.amountUzs();
        entity.paymentMethod = domain.paymentMethod();
        entity.status = domain.status();
        entity.checkoutUrl = domain.checkoutUrl();
        entity.createdAt = domain.createdAt();
        entity.paidAt = domain.paidAt();
        return entity;
    }

    public Long getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public Long getCompanyId() { return companyId; }
    public Long getUserId() { return userId; }
    public Long getAmountUzs() { return amountUzs; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public TopupStatus getStatus() { return status; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
