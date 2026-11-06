package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;

import java.time.Instant;

/** One model's priced share of one LLM charge (V43). Append-only. */
@Entity
@Table(name = "llm_usage")
public class LlmUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 24)
    private LlmUsagePurpose purpose;

    @Column(name = "reference_type", nullable = false, length = 32)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 64)
    private String referenceId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "listed", nullable = false)
    private Boolean listed;

    @Column(name = "input_tokens", nullable = false)
    private Long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private Long outputTokens;

    @Column(name = "cached_tokens", nullable = false)
    private Long cachedTokens;

    @Column(name = "input_per_million_uzs", nullable = false)
    private Long inputPerMillionUzs;

    @Column(name = "output_per_million_uzs", nullable = false)
    private Long outputPerMillionUzs;

    @Column(name = "cached_per_million_uzs", nullable = false)
    private Long cachedPerMillionUzs;

    @Column(name = "markup_basis_points", nullable = false)
    private Integer markupBasisPoints;

    @Column(name = "input_uzs", nullable = false)
    private Long inputUzs;

    @Column(name = "output_uzs", nullable = false)
    private Long outputUzs;

    @Column(name = "cached_uzs", nullable = false)
    private Long cachedUzs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public LlmUsagePurpose getPurpose() { return purpose; }
    public void setPurpose(LlmUsagePurpose purpose) { this.purpose = purpose; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Boolean getListed() { return listed; }
    public void setListed(Boolean listed) { this.listed = listed; }
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
    public Long getCachedTokens() { return cachedTokens; }
    public void setCachedTokens(Long cachedTokens) { this.cachedTokens = cachedTokens; }
    public Long getInputPerMillionUzs() { return inputPerMillionUzs; }
    public void setInputPerMillionUzs(Long inputPerMillionUzs) { this.inputPerMillionUzs = inputPerMillionUzs; }
    public Long getOutputPerMillionUzs() { return outputPerMillionUzs; }
    public void setOutputPerMillionUzs(Long outputPerMillionUzs) { this.outputPerMillionUzs = outputPerMillionUzs; }
    public Long getCachedPerMillionUzs() { return cachedPerMillionUzs; }
    public void setCachedPerMillionUzs(Long cachedPerMillionUzs) { this.cachedPerMillionUzs = cachedPerMillionUzs; }
    public Integer getMarkupBasisPoints() { return markupBasisPoints; }
    public void setMarkupBasisPoints(Integer markupBasisPoints) { this.markupBasisPoints = markupBasisPoints; }
    public Long getInputUzs() { return inputUzs; }
    public void setInputUzs(Long inputUzs) { this.inputUzs = inputUzs; }
    public Long getOutputUzs() { return outputUzs; }
    public void setOutputUzs(Long outputUzs) { this.outputUzs = outputUzs; }
    public Long getCachedUzs() { return cachedUzs; }
    public void setCachedUzs(Long cachedUzs) { this.cachedUzs = cachedUzs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
