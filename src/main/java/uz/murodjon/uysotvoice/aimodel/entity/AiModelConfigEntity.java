package uz.murodjon.uysotvoice.aimodel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code ai_model_config} (§11 settings) — a company's overrides on top
 * of the process-wide Gemini defaults ({@code spring.ai.google.genai.chat.options.*},
 * {@code DialogProperties}). Every override column is nullable: null means "use the
 * process default", not zero.
 */
@Entity
@Table(name = "ai_model_config")
public class AiModelConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Column
    private String model;

    @Column
    private Double temperature;

    @Column(name = "max_output_tokens")
    private Integer maxOutputTokens;

    @Column(name = "max_call_seconds")
    private Integer maxCallSeconds;

    @Column(name = "max_tokens_per_call")
    private Long maxTokensPerCall;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Integer getMaxCallSeconds() {
        return maxCallSeconds;
    }

    public void setMaxCallSeconds(Integer maxCallSeconds) {
        this.maxCallSeconds = maxCallSeconds;
    }

    public Long getMaxTokensPerCall() {
        return maxTokensPerCall;
    }

    public void setMaxTokensPerCall(Long maxTokensPerCall) {
        this.maxTokensPerCall = maxTokensPerCall;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
