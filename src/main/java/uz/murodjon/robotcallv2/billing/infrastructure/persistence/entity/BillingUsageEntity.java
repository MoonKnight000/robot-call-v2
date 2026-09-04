package uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;

import java.time.Instant;

@Entity
@Table(name = "billing_usage")
public class BillingUsageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
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

    public BillingUsageEntity() {
    }

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

    public BillingUsage toDomain() {
        return new BillingUsage(
                id,
                companyId,
                billingPeriod,
                usedMinutes != null ? usedMinutes : 0,
                limitMinutes != null ? limitMinutes : 5000,
                usedTokens != null ? usedTokens : 0L,
                limitTokens != null ? limitTokens : 2000000L,
                usedTtsChars != null ? usedTtsChars : 0L,
                limitTtsChars != null ? limitTtsChars : 1000000L,
                usedChannels != null ? usedChannels : 0,
                limitChannels != null ? limitChannels : 30,
                overagePriceMinute != null ? overagePriceMinute : 400.0,
                overagePriceToken != null ? overagePriceToken : 0.05,
                overagePriceTts != null ? overagePriceTts : 0.02,
                totalSpendUzs != null ? totalSpendUzs : 0L,
                createdAt,
                updatedAt
        );
    }

    public static BillingUsageEntity fromDomain(BillingUsage domain) {
        BillingUsageEntity entity = new BillingUsageEntity();
        entity.id = domain.id();
        entity.companyId = domain.companyId();
        entity.billingPeriod = domain.billingPeriod();
        entity.usedMinutes = domain.usedMinutes();
        entity.limitMinutes = domain.limitMinutes();
        entity.usedTokens = domain.usedTokens();
        entity.limitTokens = domain.limitTokens();
        entity.usedTtsChars = domain.usedTtsChars();
        entity.limitTtsChars = domain.limitTtsChars();
        entity.usedChannels = domain.usedChannels();
        entity.limitChannels = domain.limitChannels();
        entity.overagePriceMinute = domain.overagePriceMinute();
        entity.overagePriceToken = domain.overagePriceToken();
        entity.overagePriceTts = domain.overagePriceTts();
        entity.totalSpendUzs = domain.totalSpendUzs();
        entity.createdAt = domain.createdAt();
        entity.updatedAt = domain.updatedAt();
        return entity;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getBillingPeriod() { return billingPeriod; }
    public Integer getUsedMinutes() { return usedMinutes; }
    public Integer getLimitMinutes() { return limitMinutes; }
    public Long getUsedTokens() { return usedTokens; }
    public Long getLimitTokens() { return limitTokens; }
    public Long getUsedTtsChars() { return usedTtsChars; }
    public Long getLimitTtsChars() { return limitTtsChars; }
    public Integer getUsedChannels() { return usedChannels; }
    public Integer getLimitChannels() { return limitChannels; }
    public Double getOveragePriceMinute() { return overagePriceMinute; }
    public Double getOveragePriceToken() { return overagePriceToken; }
    public Double getOveragePriceTts() { return overagePriceTts; }
    public Long getTotalSpendUzs() { return totalSpendUzs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
