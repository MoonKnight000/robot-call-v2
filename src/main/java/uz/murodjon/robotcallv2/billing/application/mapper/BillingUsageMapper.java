package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingUsageEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Component
public class BillingUsageMapper {

    /**
     * The defaults stand in for a period row written before a limit column existed; the
     * domain record has no nullable counters.
     */
    public BillingUsage toBillingUsage(BillingUsageEntity entity) {
        if (entity == null) {
            return null;
        }
        return new BillingUsage(
                entity.getId(),
                entity.getCompanyId(),
                entity.getBillingPeriod(),
                entity.getUsedMinutes() != null ? entity.getUsedMinutes() : 0,
                entity.getLimitMinutes() != null ? entity.getLimitMinutes() : 5000,
                entity.getUsedTokens() != null ? entity.getUsedTokens() : 0L,
                entity.getLimitTokens() != null ? entity.getLimitTokens() : 2000000L,
                entity.getUsedTtsChars() != null ? entity.getUsedTtsChars() : 0L,
                entity.getLimitTtsChars() != null ? entity.getLimitTtsChars() : 1000000L,
                entity.getUsedChannels() != null ? entity.getUsedChannels() : 0,
                entity.getLimitChannels() != null ? entity.getLimitChannels() : 30,
                entity.getOveragePriceMinute() != null ? entity.getOveragePriceMinute() : 400.0,
                entity.getOveragePriceToken() != null ? entity.getOveragePriceToken() : 0.05,
                entity.getOveragePriceTts() != null ? entity.getOveragePriceTts() : 0.02,
                entity.getTotalSpendUzs() != null ? entity.getTotalSpendUzs() : 0L,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public BillingUsageEntity toEntity(BillingUsage usage, CompanyEntity company) {
        if (usage == null) {
            return null;
        }
        BillingUsageEntity entity = new BillingUsageEntity();
        entity.setId(usage.id());
        entity.setCompany(company);
        entity.setBillingPeriod(usage.billingPeriod());
        entity.setUsedMinutes(usage.usedMinutes());
        entity.setLimitMinutes(usage.limitMinutes());
        entity.setUsedTokens(usage.usedTokens());
        entity.setLimitTokens(usage.limitTokens());
        entity.setUsedTtsChars(usage.usedTtsChars());
        entity.setLimitTtsChars(usage.limitTtsChars());
        entity.setUsedChannels(usage.usedChannels());
        entity.setLimitChannels(usage.limitChannels());
        entity.setOveragePriceMinute(usage.overagePriceMinute());
        entity.setOveragePriceToken(usage.overagePriceToken());
        entity.setOveragePriceTts(usage.overagePriceTts());
        entity.setTotalSpendUzs(usage.totalSpendUzs());
        entity.setCreatedAt(usage.createdAt());
        entity.setUpdatedAt(usage.updatedAt());
        return entity;
    }
}
