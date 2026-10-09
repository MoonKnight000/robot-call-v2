package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallReanalysisStatus;

import java.time.Instant;

@Entity
@Table(name = "call_reanalysis_job")
public class CallReanalysisJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "ai_agent_id", nullable = false, updatable = false)
    private Long aiAgentId;

    @Column(name = "range_from", nullable = false, updatable = false)
    private Instant rangeFrom;

    @Column(name = "range_to", nullable = false, updatable = false)
    private Instant rangeTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CallReanalysisStatus status;

    @Column(name = "total_calls", nullable = false)
    private int totalCalls;

    @Column(name = "processed_calls", nullable = false)
    private int processedCalls;

    @Column(name = "updated_calls", nullable = false)
    private int updatedCalls;

    @Column(name = "failed_calls", nullable = false)
    private int failedCalls;

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "charged_uzs", nullable = false)
    private long chargedUzs;

    @Column(name = "last_call_id", nullable = false)
    private long lastCallId;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getAiAgentId() { return aiAgentId; }
    public void setAiAgentId(Long aiAgentId) { this.aiAgentId = aiAgentId; }
    public Instant getRangeFrom() { return rangeFrom; }
    public void setRangeFrom(Instant rangeFrom) { this.rangeFrom = rangeFrom; }
    public Instant getRangeTo() { return rangeTo; }
    public void setRangeTo(Instant rangeTo) { this.rangeTo = rangeTo; }
    public CallReanalysisStatus getStatus() { return status; }
    public void setStatus(CallReanalysisStatus status) { this.status = status; }
    public int getTotalCalls() { return totalCalls; }
    public void setTotalCalls(int totalCalls) { this.totalCalls = totalCalls; }
    public int getProcessedCalls() { return processedCalls; }
    public void setProcessedCalls(int processedCalls) { this.processedCalls = processedCalls; }
    public int getUpdatedCalls() { return updatedCalls; }
    public void setUpdatedCalls(int updatedCalls) { this.updatedCalls = updatedCalls; }
    public int getFailedCalls() { return failedCalls; }
    public void setFailedCalls(int failedCalls) { this.failedCalls = failedCalls; }
    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }
    public long getChargedUzs() { return chargedUzs; }
    public void setChargedUzs(long chargedUzs) { this.chargedUzs = chargedUzs; }
    public long getLastCallId() { return lastCallId; }
    public void setLastCallId(long lastCallId) { this.lastCallId = lastCallId; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
