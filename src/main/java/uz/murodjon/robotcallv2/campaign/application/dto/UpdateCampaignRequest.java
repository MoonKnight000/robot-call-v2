package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import uz.murodjon.robotcallv2.campaign.domain.enums.AgentPersona;
import uz.murodjon.robotcallv2.campaign.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * Full edit of a campaign's configuration ({@code PUT /api/campaigns/{id}}) — the same
 * shape as {@link CreateCampaignRequest}, minus the fields that are only meaningful at
 * creation time (type, script config).
 *
 * @param dialDays     weekdays this campaign may dial on ({@code ["MONDAY", ...]})
 * @param retryIntervalMinutes minutes before a client who did not answer (or whose call
 *                     failed) is dialled again; 0 leaves it to the per-disposition
 *                     defaults, which wait longer after a voicemail than after a busy
 *                     line. A retry lands inside the dial window either way
 * @param ttsVoice     id of a voice from {@code GET /api/tts/voices} (§2.5); an unknown id
 *                     is rejected
 * @param dailyCallCap most calls this campaign may place in one day; 0 for unlimited
 * @param disclosureEnabled whether calls open with the §11.1 disclosure ("Assalomu
 *                     alaykum! Bu &lt;kompaniya&gt; kompaniyasining avtomatik ovozli xizmati...")
 * @param recurrenceType repetition schedule: ONCE (default), DAILY, WEEKLY, MONTHLY, CRON
 * @param recurringDayOfMonth specific day of the month for MONTHLY recurrence (1-31)
 * @param cronExpression custom cron expression for CRON recurrence
 * @param autoResetTargets whether targets are automatically reset to PENDING when recurrence triggers
 * @param ambientSound ambient soundscape (OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE)
 * @param midCallSmsEnabled whether mid-call SMS sending is enabled
 * @param midCallSmsTemplate template for mid-call SMS messages
 * @param voicemailAction action on AMD detection (HANGUP, LEAVE_MESSAGE, IGNORE)
 * @param voicemailMessage message to speak if voicemailAction is LEAVE_MESSAGE
 * @param dtmfInputEnabled whether keypad DTMF inputs (0-9) are forwarded to dialog engine
 * @param emotionAdaptiveVoice whether voice adapts softer tone/speed upon customer frustration
 * @param sipTrunkIds  specific SIP trunk IDs to use for outbound calls. If omitted or empty,
 *                     the dialer balances across all enabled trunks of the company
 */
public record UpdateCampaignRequest(
        @NotBlank String name,
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
        RecurrenceType recurrenceType,
        @Min(1) @Max(31) Integer recurringDayOfMonth,
        String cronExpression,
        Boolean autoResetTargets,
        AmbientSound ambientSound,
        Boolean midCallSmsEnabled,
        @Size(max = 500) String midCallSmsTemplate,
        VoicemailAction voicemailAction,
        @Size(max = 500) String voicemailMessage,
        Boolean dtmfInputEnabled,
        Boolean emotionAdaptiveVoice,
        AgentPersona agentPersona,
        Map<String, String> languageVoices,
        Set<Long> sipTrunkIds) {

    public UpdateCampaignRequest(String name, String goalPrompt, String defaultLanguage,
                                 LocalTime dialWindowStart, LocalTime dialWindowEnd, Set<DayOfWeek> dialDays,
                                 int maxAttempts, int retryIntervalMinutes, int maxConcurrentCalls,
                                 String ttsVoice, int dailyCallCap) {
        this(name, goalPrompt, defaultLanguage, dialWindowStart, dialWindowEnd, dialDays,
                maxAttempts, retryIntervalMinutes, maxConcurrentCalls, ttsVoice, dailyCallCap,
                RecurrenceType.ONCE, null, null, false,
                AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true,
                AgentPersona.AI_ASSISTANT, Map.of(), Set.of());
    }

    public RecurrenceType recurrenceTypeOrDefault() {
        return recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
    }

    public AgentPersona agentPersonaOrDefault() {
        return agentPersona != null ? agentPersona : AgentPersona.AI_ASSISTANT;
    }

    public boolean autoResetTargetsOrDefault() {
        return autoResetTargets != null && autoResetTargets;
    }

    public AmbientSound ambientSoundOrDefault() {
        return ambientSound != null ? ambientSound : AmbientSound.OFF;
    }

    public boolean midCallSmsEnabledOrDefault() {
        return midCallSmsEnabled != null && midCallSmsEnabled;
    }

    public VoicemailAction voicemailActionOrDefault() {
        return voicemailAction != null ? voicemailAction : VoicemailAction.HANGUP;
    }

    public boolean dtmfInputEnabledOrDefault() {
        return dtmfInputEnabled != null && dtmfInputEnabled;
    }

    public Map<String, String> languageVoicesOrEmpty() {
        return languageVoices != null ? languageVoices : Map.of();
    }

    public boolean emotionAdaptiveVoiceOrDefault() {
        return emotionAdaptiveVoice == null || emotionAdaptiveVoice;
    }

    public Set<Long> sipTrunkIdsOrEmpty() {
        return sipTrunkIds != null ? sipTrunkIds : Set.of();
    }
}
