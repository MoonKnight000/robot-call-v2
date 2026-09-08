package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

import java.time.Instant;

@Entity
@Table(name = "billing_usage")
public class BillingUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    /** Read-only mirror of the join column, so derived queries can name it; writes go via the association. */
    @Column(name = "company_id", insertable = false, updatable = false)
    private Long companyId;

    @Column(name = "billing_period", nullable = false, length = 7)
    private String billingPeriod;

    @Column(name = "used_minutes", nullable = false)
    private Integer usedMinutes;

    @Column(name = "limit_minutes", nullable = false)
    private Integer limitMinutes;

    @Column(name = "used_tokens", nullable = false)
    private Long usedTokens;

    @Column(name = "limit_tokens", nullable = false)
    private Long limitTokens;

    @Column(name = "used_tts_chars", nullable = false)
    private Long usedTtsChars;

    @Column(name = "limit_tts_chars", nullable = false)
    private Long limitTtsChars;

    @Column(name = "used_channels", nullable = false)
    private Integer usedChannels;

    @Column(name = "limit_channels", nullable = false)
    private Integer limitChannels;

    @Column(name = "overage_price_minute", nullable = false)
    private Double overagePriceMinute;

    @Column(name = "overage_price_token", nullable = false)
    private Double overagePriceToken;

    @Column(name = "overage_price_tts", nullable = false)
    private Double overagePriceTts;

    @Column(name = "total_spend_uzs", nullable = false)
    private Long totalSpendUzs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CompanyEntity getCompany() { return company; }
    public void setCompany(CompanyEntity company) { this.company = company; }
    public Long getCompanyId() { return company != null ? company.getId() : null; }
    public String getBillingPeriod() { return billingPeriod; }
    public void setBillingPeriod(String billingPeriod) { this.billingPeriod = billingPeriod; }
    public Integer getUsedMinutes() { return usedMinutes; }
    public void setUsedMinutes(Integer usedMinutes) { this.usedMinutes = usedMinutes; }
    public Integer getLimitMinutes() { return limitMinutes; }
    public void setLimitMinutes(Integer limitMinutes) { this.limitMinutes = limitMinutes; }
    public Long getUsedTokens() { return usedTokens; }
    public void setUsedTokens(Long usedTokens) { this.usedTokens = usedTokens; }
    public Long getLimitTokens() { return limitTokens; }
    public void setLimitTokens(Long limitTokens) { this.limitTokens = limitTokens; }
    public Long getUsedTtsChars() { return usedTtsChars; }
    public void setUsedTtsChars(Long usedTtsChars) { this.usedTtsChars = usedTtsChars; }
    public Long getLimitTtsChars() { return limitTtsChars; }
    public void setLimitTtsChars(Long limitTtsChars) { this.limitTtsChars = limitTtsChars; }
    public Integer getUsedChannels() { return usedChannels; }
    public void setUsedChannels(Integer usedChannels) { this.usedChannels = usedChannels; }
    public Integer getLimitChannels() { return limitChannels; }
    public void setLimitChannels(Integer limitChannels) { this.limitChannels = limitChannels; }
    public Double getOveragePriceMinute() { return overagePriceMinute; }
    public void setOveragePriceMinute(Double overagePriceMinute) { this.overagePriceMinute = overagePriceMinute; }
    public Double getOveragePriceToken() { return overagePriceToken; }
    public void setOveragePriceToken(Double overagePriceToken) { this.overagePriceToken = overagePriceToken; }
    public Double getOveragePriceTts() { return overagePriceTts; }
    public void setOveragePriceTts(Double overagePriceTts) { this.overagePriceTts = overagePriceTts; }
    public Long getTotalSpendUzs() { return totalSpendUzs; }
    public void setTotalSpendUzs(Long totalSpendUzs) { this.totalSpendUzs = totalSpendUzs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
