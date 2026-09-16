package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentEvalRunStatus;

import java.time.Instant;

@Entity
@Table(name = "agent_eval_run")
public class AgentEvalRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "agent_version")
    private Integer agentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AgentEvalRunStatus status;

    @Column(name = "total", nullable = false)
    private int total;

    @Column(name = "passed", nullable = false)
    private int passed;

    @Column(name = "avg_qa_score")
    private Integer avgQaScore;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Integer getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(Integer agentVersion) {
        this.agentVersion = agentVersion;
    }

    public AgentEvalRunStatus getStatus() {
        return status;
    }

    public void setStatus(AgentEvalRunStatus status) {
        this.status = status;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPassed() {
        return passed;
    }

    public void setPassed(int passed) {
        this.passed = passed;
    }

    public Integer getAvgQaScore() {
        return avgQaScore;
    }

    public void setAvgQaScore(Integer avgQaScore) {
        this.avgQaScore = avgQaScore;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
