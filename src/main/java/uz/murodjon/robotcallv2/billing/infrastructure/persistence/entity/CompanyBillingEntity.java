package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;

import java.time.Instant;

@Entity
@Table(name = "company_billing")
public class CompanyBillingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 128)
    private String planName;

    @Column(name = "balance_uzs", nullable = false)
    private Long balanceUzs;

    @Column(name = "auto_recharge", nullable = false)
    private Boolean autoRecharge;

    @Column(name = "next_billing_date")
    private Instant nextBillingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CompanyBillingEntity() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public CompanyBilling toDomain() {
        return new CompanyBilling(
                id,
                companyId,
                planCode,
                planName,
                balanceUzs != null ? balanceUzs : 0L,
                Boolean.TRUE.equals(autoRecharge),
                nextBillingDate,
                createdAt,
                updatedAt
        );
    }

    public static CompanyBillingEntity fromDomain(CompanyBilling domain) {
        CompanyBillingEntity entity = new CompanyBillingEntity();
        entity.id = domain.id();
        entity.companyId = domain.companyId();
        entity.planCode = domain.planCode();
        entity.planName = domain.planName();
        entity.balanceUzs = domain.balanceUzs();
        entity.autoRecharge = domain.autoRecharge();
        entity.nextBillingDate = domain.nextBillingDate();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        return entity;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getPlanCode() { return planCode; }
    public String getPlanName() { return planName; }
    public Long getBalanceUzs() { return balanceUzs; }
    public Boolean getAutoRecharge() { return autoRecharge; }
    public Instant getNextBillingDate() { return nextBillingDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBalanceUzs(Long balanceUzs) { this.balanceUzs = balanceUzs; }
}
