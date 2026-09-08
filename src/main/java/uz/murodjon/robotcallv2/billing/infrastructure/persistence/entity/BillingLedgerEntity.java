package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.enums.LedgerEntryType;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

/** A row of the append-only money ledger. Nothing here is ever updated or deleted. */
@Entity
@Table(name = "billing_ledger")
public class BillingLedgerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 24)
    private LedgerEntryType entryType;

    @Column(name = "amount_uzs", nullable = false)
    private Long amountUzs;

    @Column(name = "balance_before_uzs", nullable = false)
    private Long balanceBeforeUzs;

    @Column(name = "balance_after_uzs", nullable = false)
    private Long balanceAfterUzs;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public void setEntryType(LedgerEntryType entryType) { this.entryType = entryType; }
    public Long getAmountUzs() { return amountUzs; }
    public void setAmountUzs(Long amountUzs) { this.amountUzs = amountUzs; }
    public void setBalanceBeforeUzs(Long balanceBeforeUzs) { this.balanceBeforeUzs = balanceBeforeUzs; }
    public void setBalanceAfterUzs(Long balanceAfterUzs) { this.balanceAfterUzs = balanceAfterUzs; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
