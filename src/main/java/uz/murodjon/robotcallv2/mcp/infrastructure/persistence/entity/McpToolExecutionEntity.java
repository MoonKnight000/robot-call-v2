package uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Entity
@Table(name = "mcp_tool_execution")
public class McpToolExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mcp_connection_id")
    private McpConnectionEntity connection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_attempt_id")
    private CallAttemptEntity callAttempt;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "args_preview", length = 500)
    private String argsPreview;

    @Column(name = "result_preview", length = 500)
    private String resultPreview;

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
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public McpConnectionEntity getConnection() { return connection; }
    public void setConnection(McpConnectionEntity connection) { this.connection = connection; }
    public void setCallAttempt(CallAttemptEntity callAttempt) { this.callAttempt = callAttempt; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public void setArgsPreview(String argsPreview) { this.argsPreview = argsPreview; }
    public void setResultPreview(String resultPreview) { this.resultPreview = resultPreview; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Instant getCreatedAt() { return createdAt; }
}
