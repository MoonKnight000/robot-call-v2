package uz.murodjon.uysotvoice.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** JPA entity for {@code audit_log} (PROJECT.md §11). */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
//todo auth qilingandan keyi actor emas employee entitysini bilan join qilish kerak
    @Column(nullable = false)
    private String actor;

    // enum qilsak yaxshi bolar edi
    @Column(nullable = false)
    private String action;

    @Column(name = "entity")
    private String entity;

    @Column(name = "entity_id")
    private String entityId;

    @Column
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
// company entitysi qoshilgandan keyin u bilan join qilish kerak
    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column(name = "ip_address")
    private String ipAddress;

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
