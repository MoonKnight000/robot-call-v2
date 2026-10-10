package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryPolicy;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

import java.util.Map;

@Component
public class CampaignEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(CampaignEntityMapper.class);

    /** Only for the {@code retry_policy} column; no configuration of its own. */
    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<Disposition, RetryRule>> RULES = new TypeReference<>() {
    };

    public CampaignEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Campaign toCampaign(CampaignEntity entity) {
        if (entity == null) {
            return null;
        }
        return Campaign.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .status(entity.getStatus())
                .dialWindowStart(entity.getDialWindowStart())
                .dialWindowEnd(entity.getDialWindowEnd())
                .dialDays(entity.getDialDays())
                .maxAttempts(entity.getMaxAttempts())
                .retryIntervalMinutes(entity.getRetryIntervalMinutes())
                .maxConcurrentCalls(entity.getMaxConcurrentCalls())
                .dailyCallCap(entity.getDailyCallCap())
                .aiAgentId(entity.getAiAgentId())
                .companyId(entity.getCompanyId())
                .createdBy(entity.getCreatedById())
                .recurrenceType(entity.getRecurrenceType())
                .recurringDayOfMonth(entity.getRecurringDayOfMonth())
                .cronExpression(entity.getCronExpression())
                .autoResetTargets(entity.isAutoResetTargets())
                .lastRunAt(entity.getLastRunAt())
                .priority(entity.getPriority())
                .retryPolicy(toRetryPolicy(entity.getRetryPolicy()))
                .startAt(entity.getStartAt())
                .endAt(entity.getEndAt())
                .budgetUzs(entity.getBudgetUzs())
                .dailyBudgetUzs(entity.getDailyBudgetUzs())
                .version(entity.getVersion())
                .dialMode(entity.getDialMode())
                .callQueueId(entity.getCallQueueId())
                .maxAiOverflowPercent(entity.getMaxAiOverflowPercent())
                .targetOrdering(entity.getTargetOrdering())
                .build();
    }

    public CampaignEntity toCampaignEntity(Campaign campaign, CompanyEntity company, UserEntity createdBy,
                                   AiAgentEntity aiAgent) {
        if (campaign == null) {
            return null;
        }
        CampaignEntity entity = new CampaignEntity();
        entity.setId(campaign.id() > 0 ? campaign.id() : null);
        entity.setScriptConfig("{}");
        entity.setType(campaign.type());
        entity.setStatus(campaign.status());
        entity.setDialMode(campaign.dialMode());
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
        entity.setCallQueueId(campaign.callQueueId());
        entity.setMaxAiOverflowPercent(campaign.maxAiOverflowPercent());
        entity.setTargetOrdering(campaign.targetOrdering());
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
        entity.setPriority(campaign.priority());
        entity.setRetryPolicy(toJson(campaign.retryPolicy()));
        entity.setStartAt(campaign.startAt());
        entity.setEndAt(campaign.endAt());
        entity.setBudgetUzs(campaign.budgetUzs());
        entity.setDailyBudgetUzs(campaign.dailyBudgetUzs());
    }

    private RetryPolicy toRetryPolicy(String json) {
        if (json == null || json.isBlank()) {
            return RetryPolicy.NONE;
        }
        try {
            return new RetryPolicy(objectMapper.readValue(json, RULES));
        } catch (Exception e) {
            // A corrupted column must not take the campaign down with it: the defaults apply.
            log.warn("campaign.retry_policy could not be read, using defaults: {}", e.getMessage());
            return RetryPolicy.NONE;
        }
    }

    private String toJson(RetryPolicy policy) {
        if (policy == null || policy.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(policy.rules());
        } catch (Exception e) {
            // A map of plain records always serialises; reaching this is a bug, not an input error.
            throw new IllegalStateException("retry policy could not be serialised", e);
        }
    }
}
