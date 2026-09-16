package uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolExecutionStatus;

import java.time.Instant;

/**
 * Plain id columns rather than associations: the row is written once on a live call and
 * never navigated, so a reference to load would only cost the turn it is written on.
 */
@Entity
@Table(name = "tool_execution")
public class ToolExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "tool_id")
    private Long toolId;

    @Column(name = "call_attempt_id")
    private Long callAttemptId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ToolExecutionStatus status;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "args_hash", length = 64)
    private String argsHash;

    @Column(name = "error", length = 500)
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getToolId() { return toolId; }
    public void setToolId(Long toolId) { this.toolId = toolId; }
    public Long getCallAttemptId() { return callAttemptId; }
    public void setCallAttemptId(Long callAttemptId) { this.callAttemptId = callAttemptId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public ToolExecutionStatus getStatus() { return status; }
    public void setStatus(ToolExecutionStatus status) { this.status = status; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getArgsHash() { return argsHash; }
    public void setArgsHash(String argsHash) { this.argsHash = argsHash; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
}
