package uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

/**
 * JPA entity for client_memory: one row per (company, phone).
 */
@Entity
@Table(name = "client_memory")
public class ClientMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false)
    private String phone;

    @Column(name = "preferred_name")
    private String preferredName;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    @Column(name = "operator_notes")
    private String operatorNotes;

    @Column(name = "recent_calls", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String recentCalls;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String facts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getOperatorNotes() {
        return operatorNotes;
    }

    public void setOperatorNotes(String operatorNotes) {
        this.operatorNotes = operatorNotes;
    }

    public String getRecentCalls() {
        return recentCalls;
    }

    public void setRecentCalls(String recentCalls) {
        this.recentCalls = recentCalls;
    }

    public String getFacts() {
        return facts;
    }

    public void setFacts(String facts) {
        this.facts = facts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
