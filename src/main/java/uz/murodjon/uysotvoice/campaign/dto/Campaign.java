package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.campaign.enums.AmbientSound;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;
import uz.murodjon.uysotvoice.campaign.enums.RecurrenceType;
import uz.murodjon.uysotvoice.campaign.enums.VoicemailAction;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A row of {@code campaign} (PROJECT.md §6).
 *
 * @param dialDays weekdays this campaign may dial on (§11.2); empty means every day
 * @param ttsVoice catalog id of the voice this campaign speaks with (§2.5); null keeps
 *                 the configured provider/voice routing
 * @param dailyCallCap most calls this campaign may dial in one day; 0 = unlimited. A cost
 *                 control, not a rate limit — {@code dispatch_batch} and
 *                 {@code max_concurrent_calls} shape the pace, this bounds the total
 * @param scenarioId the specific scenario row this campaign runs (ROADMAP A.3) —
 *                 fixed at creation, so editing that scenario later never changes what
 *                 this campaign's calls do
 * @param companyId owning company (ROADMAP B.1) — decides which SIP trunk the dialer
 *                 originates this campaign's calls through (ROADMAP B.3)
 * @param disclosureEnabled whether the §11.1 opening disclosure ("Assalomu alaykum!
 *                 Bu &lt;kompaniya&gt; kompaniyasining avtomatik ovozli xizmati...") is spoken on
 *                 this campaign's calls; the global {@code voice-agent.dialog.mandatory-
 *                 disclosure} switch still applies on top as a hard kill-switch
 * @param createdBy {@code app_user} who created this campaign; {@code null} for a
 *                 campaign created via the machine-to-machine {@code X-Api-Key} (no
 *                 associated person) or one created before this column was wired up
 * @param recurrenceType automated repetition schedule (ONCE, DAILY, WEEKLY, MONTHLY, CRON)
 * @param recurringDayOfMonth specific day of the month for MONTHLY recurrence (1-31)
 * @param cronExpression custom cron expression for CRON recurrence
 * @param autoResetTargets whether to automatically reset completed targets to PENDING upon recurrence trigger
 * @param lastRunAt timestamp when the recurring trigger was last executed
 * @param ambientSound ambient soundscape (OFF, OFFICE, CALL_CENTER, NATURAL_LINE, CAFE)
 * @param midCallSmsEnabled whether mid-call SMS sending is enabled
 * @param midCallSmsTemplate template for mid-call SMS messages
 * @param voicemailAction action on AMD detection (HANGUP, LEAVE_MESSAGE, IGNORE)
 * @param voicemailMessage message to speak if voicemailAction is LEAVE_MESSAGE
 * @param dtmfInputEnabled whether keypad DTMF inputs (0-9) are forwarded to dialog engine
 * @param emotionAdaptiveVoice whether voice adapts softer tone/speed upon customer frustration
 */
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
        boolean emotionAdaptiveVoice
) {

    /** Legacy constructor for backward compatibility. */
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
                AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true);
    }

    /** Constructor with recurrence fields but before advanced per-campaign fields. */
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
                autoResetTargets, lastRunAt, AmbientSound.OFF, false, null, VoicemailAction.HANGUP, null, false, true);
    }

    /**
     * Weekdays this campaign may dial on. An empty set allows every day — a campaign
     * created without one must not silently stop dialing, and the dial window still
     * applies.
     */
    public Set<DayOfWeek> allowedDays() {
        return dialDays == null || dialDays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : dialDays;
    }
}
