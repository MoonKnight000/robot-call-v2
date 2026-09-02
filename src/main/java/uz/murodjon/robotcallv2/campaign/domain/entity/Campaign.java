package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public record Campaign(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        String goalPrompt,
        String defaultLanguage,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        String ttsVoice,
        int dailyCallCap,
        long scenarioId,
        long companyId,
        boolean disclosureEnabled,
        Long createdBy,
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
        /**
         * Voice per call language for a campaign that dials more than one (§2.5): a
         * ru-RU target is spoken by a Russian voice and a uz-UZ one by an Uzbek voice,
         * from the same campaign. A language absent here speaks with {@code ttsVoice}.
         */
        Map<String, String> languageVoices,
        /**
         * Explicit SIP trunk IDs selected for this campaign.
         * If empty or null, the dialer balances across all enabled trunks of the company.
         */
        Set<Long> sipTrunkIds
) {

    public Campaign(
            long id,
            String name,
            CampaignType type,
            CampaignStatus status,
            String goalPrompt,
            String defaultLanguage,
            LocalTime dialWindowStart,
            LocalTime dialWindowEnd,
            Set<DayOfWeek> dialDays,
            int maxAttempts,
            int retryIntervalMinutes,
            int maxConcurrentCalls,
            String ttsVoice,
            int dailyCallCap,
            long scenarioId,
            long companyId,
            boolean disclosureEnabled,
            Long createdBy
    ) {
        this(id, name, type, status, goalPrompt, defaultLanguage, dialWindowStart, dialWindowEnd, dialDays,
                maxAttempts, retryIntervalMinutes, maxConcurrentCalls, ttsVoice, dailyCallCap, scenarioId,
                companyId, disclosureEnabled, createdBy, RecurrenceType.ONCE, null, null, false, null,
                AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true, Map.of(), Set.of());
    }

    public Campaign(
            long id,
            String name,
            CampaignType type,
            CampaignStatus status,
            String goalPrompt,
            String defaultLanguage,
            LocalTime dialWindowStart,
            LocalTime dialWindowEnd,
            Set<DayOfWeek> dialDays,
            int maxAttempts,
            int retryIntervalMinutes,
            int maxConcurrentCalls,
            String ttsVoice,
            int dailyCallCap,
            long scenarioId,
            long companyId,
            boolean disclosureEnabled,
            Long createdBy,
            RecurrenceType recurrenceType,
            Integer recurringDayOfMonth,
            String cronExpression,
            boolean autoResetTargets,
            Instant lastRunAt
    ) {
        this(id, name, type, status, goalPrompt, defaultLanguage, dialWindowStart, dialWindowEnd, dialDays,
                maxAttempts, retryIntervalMinutes, maxConcurrentCalls, ttsVoice, dailyCallCap, scenarioId,
                companyId, disclosureEnabled, createdBy, recurrenceType, recurringDayOfMonth, cronExpression,
                autoResetTargets, lastRunAt, AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true,
                Map.of(), Set.of());
    }

    public Set<DayOfWeek> allowedDays() {
        return dialDays == null || dialDays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : dialDays;
    }

    public Set<Long> sipTrunkIdsOrEmpty() {
        return sipTrunkIds == null ? Set.of() : sipTrunkIds;
    }

    /**
     * The voice a call in {@code language} speaks with: this campaign's choice for that
     * language, or its single {@code ttsVoice} when it made none.
     */
    public String voiceFor(String language) {
        if (language != null && languageVoices != null) {
            String voice = languageVoices.get(language);
            if (voice != null && !voice.isBlank()) {
                return voice;
            }
        }
        return ttsVoice;
    }
}
