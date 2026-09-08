package uz.murodjon.robotcallv2.donotcall.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;

import java.time.Instant;

/**
 * JPA entity mapping for {@code do_not_call_list} table.
 */
@Entity
@Table(name = "do_not_call_list")
public class DoNotCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private DoNotCallSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private String removedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public DoNotCallSource getSource() {
        return source;
    }

    public void setSource(DoNotCallSource source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public void setRemovedAt(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public String getRemovedBy() {
        return removedBy;
    }

    public void setRemovedBy(String removedBy) {
        this.removedBy = removedBy;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public Long getCompanyId() {
        return company != null ? company.getId() : null;
    }
}
