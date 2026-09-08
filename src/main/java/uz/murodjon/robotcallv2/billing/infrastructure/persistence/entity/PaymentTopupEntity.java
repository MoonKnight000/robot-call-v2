package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

import java.time.Instant;

@Entity
@Table(name = "payment_topup")
public class PaymentTopupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 64)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

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

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public Long getUserId() { return user != null ? user.getId() : null; }
    public Long getAmountUzs() { return amountUzs; }
    public void setAmountUzs(Long amountUzs) { this.amountUzs = amountUzs; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public TopupStatus getStatus() { return status; }
    public void setStatus(TopupStatus status) { this.status = status; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
