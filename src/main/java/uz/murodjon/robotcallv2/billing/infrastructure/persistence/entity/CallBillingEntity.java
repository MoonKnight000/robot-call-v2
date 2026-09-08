package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.enums.CallBillingStatus;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

/** One call's charge, flattened: the domain keeps usage and cost as their own records. */
@Entity
@Table(name = "call_billing")
public class CallBillingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_attempt_id", unique = true)
    private CallAttemptEntity callAttempt;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "call_attempt_id", insertable = false, updatable = false)
    private Long callAttemptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private CampaignTargetEntity target;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "target_id", insertable = false, updatable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CallBillingStatus status;

    @Column(name = "rate_version", nullable = false, length = 32)
    private String rateVersion;

    @Column(name = "reserved_uzs", nullable = false)
    private Long reservedUzs;

    @Column(name = "duration_sec", nullable = false)
    private Integer durationSec;

    @Column(name = "prompt_tokens", nullable = false)
    private Long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private Long completionTokens;

    @Column(name = "cached_tokens", nullable = false)
    private Long cachedTokens;

    @Column(name = "tts_chars", nullable = false)
    private Long ttsChars;

    @Column(name = "llm_cost_uzs", nullable = false)
    private Long llmCostUzs;

    @Column(name = "stt_cost_uzs", nullable = false)
    private Long sttCostUzs;

    @Column(name = "tts_cost_uzs", nullable = false)
    private Long ttsCostUzs;

    @Column(name = "telephony_cost_uzs", nullable = false)
    private Long telephonyCostUzs;

    @Column(name = "platform_fee_uzs", nullable = false)
    private Long platformFeeUzs;

    @Column(name = "total_uzs", nullable = false)
    private Long totalUzs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public void setCallAttempt(CallAttemptEntity callAttempt) { this.callAttempt = callAttempt; }
    public Long getCallAttemptId() { return callAttempt != null ? callAttempt.getId() : null; }
    public CampaignTargetEntity getTarget() { return target; }
    public void setTarget(CampaignTargetEntity target) { this.target = target; }
    public Long getTargetId() { return target != null ? target.getId() : null; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public CallBillingStatus getStatus() { return status; }
    public void setStatus(CallBillingStatus status) { this.status = status; }
    public String getRateVersion() { return rateVersion; }
    public void setRateVersion(String rateVersion) { this.rateVersion = rateVersion; }
    public Long getReservedUzs() { return reservedUzs; }
    public void setReservedUzs(Long reservedUzs) { this.reservedUzs = reservedUzs; }
    public Integer getDurationSec() { return durationSec; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }
    public Long getCachedTokens() { return cachedTokens; }
    public void setCachedTokens(Long cachedTokens) { this.cachedTokens = cachedTokens; }
    public Long getTtsChars() { return ttsChars; }
    public void setTtsChars(Long ttsChars) { this.ttsChars = ttsChars; }
    public Long getLlmCostUzs() { return llmCostUzs; }
    public void setLlmCostUzs(Long llmCostUzs) { this.llmCostUzs = llmCostUzs; }
    public Long getSttCostUzs() { return sttCostUzs; }
    public void setSttCostUzs(Long sttCostUzs) { this.sttCostUzs = sttCostUzs; }
    public Long getTtsCostUzs() { return ttsCostUzs; }
    public void setTtsCostUzs(Long ttsCostUzs) { this.ttsCostUzs = ttsCostUzs; }
    public Long getTelephonyCostUzs() { return telephonyCostUzs; }
    public void setTelephonyCostUzs(Long telephonyCostUzs) { this.telephonyCostUzs = telephonyCostUzs; }
    public Long getPlatformFeeUzs() { return platformFeeUzs; }
    public void setPlatformFeeUzs(Long platformFeeUzs) { this.platformFeeUzs = platformFeeUzs; }
    public Long getTotalUzs() { return totalUzs; }
    public void setTotalUzs(Long totalUzs) { this.totalUzs = totalUzs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
}
