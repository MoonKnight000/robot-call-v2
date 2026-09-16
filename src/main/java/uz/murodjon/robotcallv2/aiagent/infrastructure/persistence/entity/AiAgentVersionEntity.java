package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;

import java.time.Instant;

@Entity
@Table(name = "ai_agent_version")
public class AiAgentVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AgentVersionStatus status;

    /** The whole {@code AiAgent} as JSON, written and read by {@code AiAgentVersionEntityMapper}. */
    @Column(name = "snapshot", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String snapshot;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    public Long getId() {
        return id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public AgentVersionStatus getStatus() {
        return status;
    }

    public void setStatus(AgentVersionStatus status) {
        this.status = status;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Long publishedBy) {
        this.publishedBy = publishedBy;
    }
}
