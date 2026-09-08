package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

/**
 * JPA entity for ai_model_config (§11 settings).
 */
@Entity
@Table(name = "ai_model_config")
public class AiModelConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

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

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
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
