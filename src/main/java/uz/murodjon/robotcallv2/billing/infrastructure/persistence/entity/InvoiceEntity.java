package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;

import java.time.Instant;

@Entity
@Table(name = "invoice")
public class InvoiceEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "company_id", nullable = false)
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

    public InvoiceEntity() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Invoice toDomain() {
        return new Invoice(
                id,
                companyId,
                periodName,
                amountUzs != null ? amountUzs : 0L,
                status != null ? status : InvoiceStatus.PENDING,
                paidAt,
                pdfFilePath,
                createdAt
        );
    }

    public static InvoiceEntity fromDomain(Invoice domain) {
        InvoiceEntity entity = new InvoiceEntity();
        entity.id = domain.id();
        entity.companyId = domain.companyId();
        entity.periodName = domain.periodName();
        entity.amountUzs = domain.amountUzs();
        entity.status = domain.status();
        entity.paidAt = domain.paidAt();
        entity.pdfFilePath = domain.pdfFilePath();
        entity.createdAt = domain.createdAt();
        return entity;
    }

    public String getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getPeriodName() { return periodName; }
    public Long getAmountUzs() { return amountUzs; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public String getPdfFilePath() { return pdfFilePath; }
    public Instant getCreatedAt() { return createdAt; }
}
