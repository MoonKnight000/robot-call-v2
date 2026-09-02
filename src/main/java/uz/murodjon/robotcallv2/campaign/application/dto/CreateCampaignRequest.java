package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import uz.murodjon.robotcallv2.campaign.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;

/**
 * @param dialDays     weekdays this campaign may dial on ({@code ["MONDAY", ...]}); omit
 *                     or leave empty for Monday-Friday (§11.2)
 * @param ttsVoice     id of a voice from {@code GET /api/tts/voices} (§2.5); omit to
 *                     speak with the configured default. An unknown id is rejected
 * @param dailyCallCap most calls this campaign may place in one day; 0 or omitted for
 *                     unlimited. A spend ceiling — every call costs STT, LLM, TTS and trunk
 *                     minutes, and a campaign with 50 000 targets will spend them all
 * @param scenarioId   id of a scenario row from {@code GET /api/scenarios} (ROADMAP A.3)
 *                     this campaign's calls run. Fixed for the campaign's lifetime — to
 *                     run a different scenario, create a new campaign
 * @param type         required (backend-uchun-talablar.md §9a) — previously an omitted/blank
 *                     value silently fell back to {@code DEBT_COLLECTION}, which surprised
 *                     callers that meant to leave it unset; a missing value is now a 400
 * @param retryIntervalMinutes minutes before a client who did not answer (or whose call
 *                     failed) is dialled again; 0 leaves it to the per-disposition
 *                     defaults, which wait longer after a voicemail than after a busy
 *                     line. A retry lands inside the dial window either way
 * @param disclosureEnabled whether calls open with the §11.1 disclosure ("Assalomu
 *                     alaykum! Bu &lt;kompaniya&gt; kompaniyasining avtomatik ovozli xizmati...");
 *                     omit for the default (enabled)
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
 * @param languageVoices voice per call language for a campaign that dials more than one
 *                     ({@code {"uz-UZ": "nigora", "ru-RU": "alena"}}): a target marked
 *                     ru-RU is spoken by the Russian voice, an uz-UZ one by the Uzbek
 *                     voice, and the language the caller actually speaks switches it
 *                     mid-call. A language omitted here speaks with {@code ttsVoice}.
 *                     Every id must come from {@code GET /api/tts/voices} and must speak
 *                     the language it is mapped to — anything else is a 400
 * @param sipTrunkIds  specific SIP trunk IDs to use for outbound calls. If omitted or empty,
 *                     the dialer balances across all enabled trunks of the company
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull CampaignType type,
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
        @NotNull Long scenarioId,
        Boolean disclosureEnabled,
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
        Map<String, String> languageVoices,
        Set<Long> sipTrunkIds) {

    public CreateCampaignRequest(String name, CampaignType type, String goalPrompt, String defaultLanguage,
                                 LocalTime dialWindowStart, LocalTime dialWindowEnd, Set<DayOfWeek> dialDays,
                                 int maxAttempts, int retryIntervalMinutes, int maxConcurrentCalls,
                                 String ttsVoice, int dailyCallCap, Long scenarioId, Boolean disclosureEnabled) {
        this(name, type, goalPrompt, defaultLanguage, dialWindowStart, dialWindowEnd, dialDays,
                maxAttempts, retryIntervalMinutes, maxConcurrentCalls, ttsVoice, dailyCallCap, scenarioId,
                disclosureEnabled, RecurrenceType.ONCE, null, null, false,
                AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true, Map.of(), Set.of());
    }

    public RecurrenceType recurrenceTypeOrDefault() {
        return recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
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

    public boolean emotionAdaptiveVoiceOrDefault() {
        return emotionAdaptiveVoice == null || emotionAdaptiveVoice;
    }

    public Map<String, String> languageVoicesOrEmpty() {
        return languageVoices != null ? languageVoices : Map.of();
    }

    public Set<Long> sipTrunkIdsOrEmpty() {
        return sipTrunkIds != null ? sipTrunkIds : Set.of();
    }
}
