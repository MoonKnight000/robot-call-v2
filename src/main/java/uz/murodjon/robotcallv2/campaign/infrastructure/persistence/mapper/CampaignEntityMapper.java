package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialing;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignNumbers;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRecurrence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSequence;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
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
                .status(entity.getStatus())
                .aiAgentId(entity.getAiAgentId())
                .companyId(entity.getCompanyId())
                .createdBy(entity.getCreatedById())
                .priority(entity.getPriority())
                .schedule(new CampaignSchedule(entity.getStartAt(), entity.getEndAt(),
                        entity.getDialWindowStart(), entity.getDialWindowEnd(), entity.getDialDays()))
                .retry(new CampaignRetry(entity.getMaxAttempts(), entity.getRetryIntervalMinutes(),
                        toRetryPolicy(entity.getRetryPolicy())))
                .limits(new CampaignLimits(entity.getMaxConcurrentCalls(), entity.getDailyCallCap(),
                        entity.getBudgetUzs(), entity.getDailyBudgetUzs(), entity.getMaxCps()))
                .recurrence(new CampaignRecurrence(entity.getRecurrenceType(), entity.getRecurringDayOfMonth(),
                        entity.getCronExpression(), entity.isAutoResetTargets()))
                .dialing(new CampaignDialing(entity.getDialMode(), entity.getCallQueueId(),
                        entity.getMaxAiOverflowPercent(), entity.getTargetOrdering()))
                .numbers(new CampaignNumbers(entity.getCallerIdPoolId(), entity.isLocalPresence()))
                .kind(entity.getKind())
                .sequence(toSequence(entity.getSequence()))
                .autoEnroll(toAutoEnrollment(entity.getAutoEnroll()))
                .consentConfirmedAt(entity.getConsentConfirmedAt())
                .lastRunAt(entity.getLastRunAt())
                .version(entity.getVersion())
                .build();
    }

    public CampaignEntity toCampaignEntity(Campaign campaign, CompanyEntity company, UserEntity createdBy,
                                   AiAgentEntity aiAgent) {
        if (campaign == null) {
            return null;
        }
        CampaignEntity entity = new CampaignEntity();
        entity.setId(campaign.id() > 0 ? campaign.id() : null);
        entity.setType(entity.getType() != null ? entity.getType() : "OUTBOUND");
        entity.setStatus(campaign.status());
        entity.setDialMode(campaign.dialing().mode());
        entity.setKind(campaign.kind());
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
        entity.setCallQueueId(campaign.dialing().callQueueId());
        entity.setMaxAiOverflowPercent(campaign.dialing().maxAiOverflowPercent());
        entity.setTargetOrdering(campaign.dialing().targetOrdering());
        entity.setDialWindowStart(campaign.schedule().dialWindowStart());
        entity.setDialWindowEnd(campaign.schedule().dialWindowEnd());
        entity.setDialDays(campaign.schedule().dialDays());
        entity.setMaxAttempts(campaign.retry().maxAttempts());
        entity.setRetryIntervalMinutes(campaign.retry().intervalMinutes());
        entity.setMaxConcurrentCalls(campaign.limits().maxConcurrentCalls());
        entity.setDailyCallCap(campaign.limits().dailyCallCap());
        entity.setMaxCps(campaign.limits().maxCps());
        entity.setRecurrenceType(campaign.recurrence().type());
        entity.setRecurringDayOfMonth(campaign.recurrence().dayOfMonth());
        entity.setCronExpression(campaign.recurrence().cronExpression());
        entity.setAutoResetTargets(campaign.recurrence().autoResetTargets());
        entity.setPriority(campaign.priority());
        entity.setRetryPolicy(toJson(campaign.retry().policy()));
        entity.setSequence(toJson(campaign.sequence()));
        entity.setAutoEnroll(campaign.autoEnroll().hasNoRules() ? null : toJson(campaign.autoEnroll()));
        entity.setCallerIdPoolId(campaign.numbers().callerIdPoolId());
        entity.setLocalPresence(campaign.numbers().localPresence());
        entity.setStartAt(campaign.schedule().startAt());
        entity.setEndAt(campaign.schedule().endAt());
        entity.setBudgetUzs(campaign.limits().budgetUzs());
        entity.setDailyBudgetUzs(campaign.limits().dailyBudgetUzs());
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

    private CampaignSequence toSequence(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CampaignSequence.class);
        } catch (Exception e) {
            // Read as a campaign from before sequences rather than failing every listing.
            log.warn("campaign.sequence could not be read, treating as none: {}", e.getMessage());
            return null;
        }
    }

    private AutoEnrollment toAutoEnrollment(String json) {
        if (json == null || json.isBlank()) {
            return AutoEnrollment.NONE;
        }
        try {
            return objectMapper.readValue(json, AutoEnrollment.class);
        } catch (Exception e) {
            // Enrolls nobody rather than failing every listing.
            log.warn("campaign.auto_enroll could not be read, treating as none: {}", e.getMessage());
            return AutoEnrollment.NONE;
        }
    }

    private String toJson(AutoEnrollment autoEnroll) {
        try {
            return objectMapper.writeValueAsString(autoEnroll);
        } catch (Exception e) {
            // A tree of plain records always serialises; reaching this is a bug, not an input error.
            throw new IllegalStateException("campaign auto-enroll rules could not be serialised", e);
        }
    }

    private String toJson(CampaignSequence sequence) {
        if (sequence == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sequence);
        } catch (Exception e) {
            // A tree of plain records always serialises; reaching this is a bug, not an input error.
            throw new IllegalStateException("campaign sequence could not be serialised", e);
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
