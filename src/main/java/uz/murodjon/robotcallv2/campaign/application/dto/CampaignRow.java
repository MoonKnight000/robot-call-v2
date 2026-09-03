package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTargetStats;
import uz.murodjon.robotcallv2.campaign.domain.enums.*;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * {@link Campaign} enriched for the API response with names resolved from the ids it
 * carries (backend-uchun-talablar.md §16/§18) — a projection over {@code campaign},
 * {@code scenario} and {@code app_user}, so it takes the {@code <Noun>Row} suffix rather
 * than bare {@code Campaign}.
 */
public record CampaignRow(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        String goalPrompt,
        String defaultLanguage,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        String ttsVoice,
        int dailyCallCap,
        long scenarioId,
        String scenarioName,
        long companyId,
        boolean disclosureEnabled,
        Long createdBy,
        String createdByName,
        RecurrenceType recurrenceType,
        Integer recurringDayOfMonth,
        String cronExpression,
        boolean autoResetTargets,
        Instant lastRunAt,
        AmbientSound ambientSound,
        boolean midCallSmsEnabled,
        String midCallSmsTemplate,
        VoicemailAction voicemailAction,
        String voicemailMessage,
        boolean dtmfInputEnabled,
        boolean emotionAdaptiveVoice,
        Map<String, String> languageVoices,
        Set<Long> sipTrunkIds,
        long totalTargets,
        long calledTargets,
        long pendingTargets,
        long completedTargets
) {

    public static CampaignRow of(Campaign c, String scenarioName, String createdByName) {
        return of(c, scenarioName, createdByName, CampaignTargetStats.ZERO);
    }

    public static CampaignRow of(Campaign c, String scenarioName, String createdByName, CampaignTargetStats stats) {
        CampaignTargetStats s = stats != null ? stats : CampaignTargetStats.ZERO;
        return new CampaignRow(
                c.id(), c.name(), c.type(), c.status(), c.goalPrompt(), c.defaultLanguage(),
                c.dialWindowStart(), c.dialWindowEnd(), c.dialDays(), c.maxAttempts(),
                c.retryIntervalMinutes(), c.maxConcurrentCalls(), c.ttsVoice(), c.dailyCallCap(),
                c.scenarioId(), scenarioName, c.companyId(), c.disclosureEnabled(),
                c.createdBy(), createdByName,
                c.recurrenceType() != null ? c.recurrenceType() : RecurrenceType.ONCE,
                c.recurringDayOfMonth(), c.cronExpression(), c.autoResetTargets(), c.lastRunAt(),
                c.ambientSound() != null ? c.ambientSound() : AmbientSound.OFF,
                c.midCallSmsEnabled(),
                c.midCallSmsTemplate(),
                c.voicemailAction() != null ? c.voicemailAction() : VoicemailAction.HANGUP,
                c.voicemailMessage(),
                c.dtmfInputEnabled(),
                c.emotionAdaptiveVoice(),
                c.languageVoices(),
                c.sipTrunkIdsOrEmpty(),
                s.totalTargets(),
                s.calledTargets(),
                s.pendingTargets(),
                s.completedTargets());
    }
}
