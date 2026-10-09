package uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;

import java.time.Instant;

@Entity
@Table(name = "copilot_message")
public class CopilotMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16, updatable = false)
    private CopilotMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "tool_name", length = 100, updatable = false)
    private String toolName;

    @Column(name = "tool_arguments", columnDefinition = "TEXT", updatable = false)
    private String toolArguments;

    @Column(name = "tool_error", nullable = false, updatable = false)
    private boolean toolError;

    @Column(name = "prompt_tokens", nullable = false, updatable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false, updatable = false)
    private long completionTokens;

    /** Written after the reply is charged, by {@code CopilotMessageJpaRepository.updateChargedUzs}. */
    @Column(name = "charged_uzs", nullable = false, updatable = false)
    private long chargedUzs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public CopilotMessageRole getRole() {
        return role;
    }

    public void setRole(CopilotMessageRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolArguments() {
        return toolArguments;
    }

    public void setToolArguments(String toolArguments) {
        this.toolArguments = toolArguments;
    }

    public boolean isToolError() {
        return toolError;
    }

    public void setToolError(boolean toolError) {
        this.toolError = toolError;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public long getChargedUzs() {
        return chargedUzs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
