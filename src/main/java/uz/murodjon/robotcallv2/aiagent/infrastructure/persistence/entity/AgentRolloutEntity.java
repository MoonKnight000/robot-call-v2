package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ai_agent_rollout")
public class AgentRolloutEntity {

    @Id
    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "candidate_version", nullable = false, updatable = false)
    private int candidateVersion;

    @Column(name = "traffic_percent", nullable = false)
    private int trafficPercent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "staging_phones", columnDefinition = "jsonb", nullable = false)
    private List<String> stagingPhones = new ArrayList<>();

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "started_by", updatable = false)
    private Long startedBy;

    @PrePersist
    public void prePersist() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public int getCandidateVersion() { return candidateVersion; }
    public void setCandidateVersion(int candidateVersion) { this.candidateVersion = candidateVersion; }
    public int getTrafficPercent() { return trafficPercent; }
    public void setTrafficPercent(int trafficPercent) { this.trafficPercent = trafficPercent; }
    public List<String> getStagingPhones() { return stagingPhones; }
    public void setStagingPhones(List<String> stagingPhones) { this.stagingPhones = new ArrayList<>(stagingPhones); }
    public Instant getStartedAt() { return startedAt; }
    public Long getStartedBy() { return startedBy; }
    public void setStartedBy(Long startedBy) { this.startedBy = startedBy; }
}
