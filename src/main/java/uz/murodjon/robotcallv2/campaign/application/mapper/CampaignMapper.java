package uz.murodjon.robotcallv2.campaign.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

import java.util.Map;
import java.util.Set;

@Component
public class CampaignMapper {

    public Campaign toDomain(CampaignEntity e) {
        if (e == null) return null;
        return new Campaign(
                e.getId(),
                e.getName(),
                e.getType(),
                e.getStatus(),
                e.getGoalPrompt(),
                e.getDefaultLanguage(),
                e.getDialWindowStart(),
                e.getDialWindowEnd(),
                e.getDialDays(),
                e.getMaxAttempts(),
                e.getRetryIntervalMinutes(),
                e.getMaxConcurrentCalls(),
                e.getTtsVoice(),
                e.getDailyCallCap(),
                e.getScenarioId(),
                e.getCompanyId(),
                e.isDisclosureEnabled(),
                e.getCreatedById(),
                e.getRecurrenceType(),
                e.getRecurringDayOfMonth(),
                e.getCronExpression(),
                e.isAutoResetTargets(),
                e.getLastRunAt(),
                e.getAmbientSound(),
                e.isMidCallSmsEnabled(),
                e.getMidCallSmsTemplate(),
                e.getVoicemailAction(),
                e.getVoicemailMessage(),
                e.isDtmfInputEnabled(),
                e.isEmotionAdaptiveVoice(),
                Map.copyOf(e.getLanguageVoices()),
                Set.copyOf(e.getSipTrunkIds())
        );
    }

    public CampaignEntity toEntity(Campaign d, CompanyEntity company, ScenarioEntity scenario, UserEntity createdBy) {
        if (d == null) return null;
        CampaignEntity e = new CampaignEntity();
        e.setId(d.id() > 0 ? d.id() : null);
        e.setName(d.name());
        e.setType(d.type());
        e.setStatus(d.status());
        e.setGoalPrompt(d.goalPrompt());
        e.setScriptConfig("{}");
        e.setDefaultLanguage(d.defaultLanguage());
        e.setDialWindowStart(d.dialWindowStart());
        e.setDialWindowEnd(d.dialWindowEnd());
        e.setDialDays(d.dialDays());
        e.setMaxAttempts(d.maxAttempts());
        e.setRetryIntervalMinutes(d.retryIntervalMinutes());
        e.setMaxConcurrentCalls(d.maxConcurrentCalls());
        e.setTtsVoice(d.ttsVoice());
        e.setDailyCallCap(d.dailyCallCap());
        e.setCompany(company);
        e.setScenario(scenario);
        e.setDisclosureEnabled(d.disclosureEnabled());
        e.setCreatedBy(createdBy);
        e.setRecurrenceType(d.recurrenceType());
        e.setRecurringDayOfMonth(d.recurringDayOfMonth());
        e.setCronExpression(d.cronExpression());
        e.setAutoResetTargets(d.autoResetTargets());
        e.setLastRunAt(d.lastRunAt());
        e.setAmbientSound(d.ambientSound());
        e.setMidCallSmsEnabled(d.midCallSmsEnabled());
        e.setMidCallSmsTemplate(d.midCallSmsTemplate());
        e.setVoicemailAction(d.voicemailAction());
        e.setVoicemailMessage(d.voicemailMessage());
        e.setDtmfInputEnabled(d.dtmfInputEnabled());
        e.setEmotionAdaptiveVoice(d.emotionAdaptiveVoice());
        e.setLanguageVoices(d.languageVoices());
        e.setSipTrunkIds(d.sipTrunkIds());
        return e;
    }
}
