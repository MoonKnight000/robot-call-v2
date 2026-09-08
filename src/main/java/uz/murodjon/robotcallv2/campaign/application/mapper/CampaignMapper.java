package uz.murodjon.robotcallv2.campaign.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

@Component
public class CampaignMapper {

    public Campaign toCampaign(CampaignEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Campaign(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getStatus(),
                entity.getDialWindowStart(),
                entity.getDialWindowEnd(),
                entity.getDialDays(),
                entity.getMaxAttempts(),
                entity.getRetryIntervalMinutes(),
                entity.getMaxConcurrentCalls(),
                entity.getDailyCallCap(),
                entity.getAiAgentId(),
                entity.getCompanyId(),
                entity.getCreatedById(),
                entity.getRecurrenceType(),
                entity.getRecurringDayOfMonth(),
                entity.getCronExpression(),
                entity.isAutoResetTargets(),
                entity.getLastRunAt());
    }

    public CampaignEntity toEntity(Campaign campaign, CompanyEntity company, UserEntity createdBy,
                                   AiAgentEntity aiAgent) {
        if (campaign == null) {
            return null;
        }
        CampaignEntity entity = new CampaignEntity();
        entity.setId(campaign.id() > 0 ? campaign.id() : null);
        entity.setScriptConfig("{}");
        entity.setType(campaign.type());
        entity.setStatus(campaign.status());
        entity.setCompany(company);
        entity.setCreatedBy(createdBy);
        entity.setLastRunAt(campaign.lastRunAt());
        applyEditableFields(entity, campaign, aiAgent);
        return entity;
    }

    /** Everything an edit may change — the same set {@code PUT /api/campaigns/{id}} sends. */
    public void applyEditableFields(CampaignEntity entity, Campaign campaign, AiAgentEntity aiAgent) {
        entity.setName(campaign.name());
        entity.setAiAgent(aiAgent);
        entity.setDialWindowStart(campaign.dialWindowStart());
        entity.setDialWindowEnd(campaign.dialWindowEnd());
        entity.setDialDays(campaign.dialDays());
        entity.setMaxAttempts(campaign.maxAttempts());
        entity.setRetryIntervalMinutes(campaign.retryIntervalMinutes());
        entity.setMaxConcurrentCalls(campaign.maxConcurrentCalls());
        entity.setDailyCallCap(campaign.dailyCallCap());
        entity.setRecurrenceType(campaign.recurrenceType());
        entity.setRecurringDayOfMonth(campaign.recurringDayOfMonth());
        entity.setCronExpression(campaign.cronExpression());
        entity.setAutoResetTargets(campaign.autoResetTargets());
    }
}
