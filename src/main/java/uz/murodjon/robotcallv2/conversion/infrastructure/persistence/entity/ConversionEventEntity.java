package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Entity
@Table(name = "conversion_event")
public class ConversionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "goal_key", nullable = false, length = 64)
    private String goalKey;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "value_uzs")
    private Long valueUzs;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "evidence")
    @JdbcTypeCode(SqlTypes.JSON)
    private String evidence;

    @Column(name = "dedupe_key", nullable = false, length = 128)
    private String dedupeKey;

    @Column(name = "rejected", nullable = false)
    private Boolean rejected;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt;

    @PrePersist
    public void prePersist() {
        if (ingestedAt == null) {
            ingestedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public String getGoalKey() { return goalKey; }
    public void setGoalKey(String goalKey) { this.goalKey = goalKey; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Long getValueUzs() { return valueUzs; }
    public void setValueUzs(Long valueUzs) { this.valueUzs = valueUzs; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
    public Boolean getRejected() { return rejected; }
    public void setRejected(Boolean rejected) { this.rejected = rejected; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getIngestedAt() { return ingestedAt; }
    public void setIngestedAt(Instant ingestedAt) { this.ingestedAt = ingestedAt; }
}
