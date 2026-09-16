package uz.murodjon.robotcallv2.insight.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightStatus;
import uz.murodjon.robotcallv2.insight.domain.enums.InsightType;

import java.time.Instant;

/**
 * Plain id columns rather than associations, like {@code call_quality}: the row is written by
 * a job, decided once, and read back whole.
 */
@Entity
@Table(name = "agent_insight")
public class AgentInsightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "ai_agent_id", nullable = false)
    private Long aiAgentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private InsightType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "call_count", nullable = false)
    private int callCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sample_call_ids", nullable = false, columnDefinition = "jsonb")
    private String sampleCallIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InsightStatus status;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "eval_run_id")
    private Long evalRunId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getAiAgentId() {
        return aiAgentId;
    }

    public void setAiAgentId(Long aiAgentId) {
        this.aiAgentId = aiAgentId;
    }

    public InsightType getType() {
        return type;
    }

    public void setType(InsightType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public int getCallCount() {
        return callCount;
    }

    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }

    public String getSampleCallIds() {
        return sampleCallIds;
    }

    public void setSampleCallIds(String sampleCallIds) {
        this.sampleCallIds = sampleCallIds;
    }

    public InsightStatus getStatus() {
        return status;
    }

    public void setStatus(InsightStatus status) {
        this.status = status;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(Long decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public Long getEvalRunId() {
        return evalRunId;
    }

    public void setEvalRunId(Long evalRunId) {
        this.evalRunId = evalRunId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
