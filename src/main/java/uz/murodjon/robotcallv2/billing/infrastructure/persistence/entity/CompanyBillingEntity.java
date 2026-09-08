package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Entity
@Table(name = "company_billing")
public class CompanyBillingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "plan_code", nullable = false, length = 64)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 128)
    private String planName;

    @Column(name = "balance_uzs", nullable = false)
    private Long balanceUzs;

    @Column(name = "reserved_uzs", nullable = false)
    private Long reservedUzs;

    @Column(name = "auto_recharge", nullable = false)
    private Boolean autoRecharge;

    @Column(name = "next_billing_date")
    private Instant nextBillingDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getBalanceUzs() { return balanceUzs; }
    public void setBalanceUzs(Long balanceUzs) { this.balanceUzs = balanceUzs; }
    public Long getReservedUzs() { return reservedUzs; }
    public void setReservedUzs(Long reservedUzs) { this.reservedUzs = reservedUzs; }
    public Boolean getAutoRecharge() { return autoRecharge; }
    public void setAutoRecharge(Boolean autoRecharge) { this.autoRecharge = autoRecharge; }
    public Instant getNextBillingDate() { return nextBillingDate; }
    public void setNextBillingDate(Instant nextBillingDate) { this.nextBillingDate = nextBillingDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
