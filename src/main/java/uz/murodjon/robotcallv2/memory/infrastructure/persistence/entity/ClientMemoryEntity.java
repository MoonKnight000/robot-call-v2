package uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity for client_memory: one row per (company, phone).
 */
@Entity
@Table(name = "client_memory")
public class ClientMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

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

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
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
