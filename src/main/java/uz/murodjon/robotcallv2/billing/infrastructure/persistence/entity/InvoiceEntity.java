package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Entity
@Table(name = "invoice")
public class InvoiceEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "period_name", nullable = false, length = 64)
    private String periodName;

    @Column(name = "amount_uzs", nullable = false)
    private Long amountUzs;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InvoiceStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "pdf_file_path", length = 255)
    private String pdfFilePath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public String getPeriodName() { return periodName; }
    public void setPeriodName(String periodName) { this.periodName = periodName; }
    public Long getAmountUzs() { return amountUzs; }
    public void setAmountUzs(Long amountUzs) { this.amountUzs = amountUzs; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public String getPdfFilePath() { return pdfFilePath; }
    public void setPdfFilePath(String pdfFilePath) { this.pdfFilePath = pdfFilePath; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
