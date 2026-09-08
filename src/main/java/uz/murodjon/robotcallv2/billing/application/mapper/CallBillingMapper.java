package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.CallBilling;
import uz.murodjon.robotcallv2.billing.domain.entity.CallCostBreakdown;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CallBillingEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignTargetEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

/** The table is flat; the domain keeps usage and cost as their own records. */
@Component
public class CallBillingMapper {

    public CallBilling toCallBilling(CallBillingEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CallBilling(
                entity.getId(),
                entity.getCallAttemptId(),
                entity.getTargetId(),
                entity.getCompanyId(),
                entity.getStatus(),
                entity.getRateVersion(),
                entity.getReservedUzs(),
                new CallUsage(
                        entity.getDurationSec(),
                        entity.getPromptTokens(),
                        entity.getCompletionTokens(),
                        entity.getCachedTokens(),
                        entity.getTtsChars()),
                new CallCostBreakdown(
                        entity.getLlmCostUzs(),
                        entity.getSttCostUzs(),
                        entity.getTtsCostUzs(),
                        entity.getTelephonyCostUzs(),
                        entity.getPlatformFeeUzs(),
                        entity.getTotalUzs()),
                entity.getCreatedAt(),
                entity.getSettledAt());
    }

    public CallBillingEntity toEntity(CallBilling billing, CompanyEntity company,
                                      CallAttemptEntity callAttempt, CampaignTargetEntity target) {
        if (billing == null) {
            return null;
        }
        CallBillingEntity entity = new CallBillingEntity();
        entity.setId(billing.id());
        entity.setCallAttempt(callAttempt);
        entity.setTarget(target);
        entity.setCompany(company);
        entity.setStatus(billing.status());
        entity.setRateVersion(billing.rateVersion());
        entity.setReservedUzs(billing.reservedUzs());
        entity.setDurationSec(billing.usage().durationSec());
        entity.setPromptTokens(billing.usage().promptTokens());
        entity.setCompletionTokens(billing.usage().completionTokens());
        entity.setCachedTokens(billing.usage().cachedTokens());
        entity.setTtsChars(billing.usage().ttsChars());
        entity.setLlmCostUzs(billing.cost().llmUzs());
        entity.setSttCostUzs(billing.cost().sttUzs());
        entity.setTtsCostUzs(billing.cost().ttsUzs());
        entity.setTelephonyCostUzs(billing.cost().telephonyUzs());
        entity.setPlatformFeeUzs(billing.cost().platformFeeUzs());
        entity.setTotalUzs(billing.cost().totalUzs());
        entity.setCreatedAt(billing.createdAt());
        entity.setSettledAt(billing.settledAt());
        return entity;
    }
}
