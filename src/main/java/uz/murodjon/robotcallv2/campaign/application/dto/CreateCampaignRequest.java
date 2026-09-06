package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * A new calling job ({@code POST /api/campaigns}).
 *
 * <p>Only who is called and when. The voice, the persona, the scenario, the model and the
 * trunks come from the {@code aiAgentId} — see {@code POST /api/ai-agents}.
 *
 * @param aiAgentId    required: the agent from {@code GET /api/ai-agents} whose voice and
 *                     scenario this campaign's calls run under. Changeable later — an
 *                     agent is a way of speaking, not the campaign's identity
 * @param dialDays     weekdays this campaign may dial on ({@code ["MONDAY", ...]}); omit
 *                     or leave empty for Monday-Friday (§11.2)
 * @param dailyCallCap most calls this campaign may place in one day; 0 or omitted for
 *                     unlimited. A spend ceiling — every call costs STT, LLM, TTS and trunk
 *                     minutes, and a campaign with 50 000 targets will spend them all
 * @param type         required (backend-uchun-talablar.md §9a) — previously an omitted/blank
 *                     value silently fell back to {@code DEBT_COLLECTION}, which surprised
 *                     callers that meant to leave it unset; a missing value is now a 400
 * @param retryIntervalMinutes minutes before a client who did not answer (or whose call
 *                     failed) is dialled again; 0 leaves it to the per-disposition
 *                     defaults, which wait longer after a voicemail than after a busy
 *                     line. A retry lands inside the dial window either way
 * @param recurrenceType repetition schedule: ONCE (default), DAILY, WEEKLY, MONTHLY, CRON.
 *                     Anything but ONCE re-runs the campaign on its own; pair it with a
 *                     target source ({@code PUT /api/campaigns/{id}/target-source}) to have
 *                     each run dial a freshly fetched list instead of the same one
 * @param recurringDayOfMonth specific day of the month for MONTHLY recurrence (1-31)
 * @param cronExpression custom cron expression for CRON recurrence
 * @param autoResetTargets whether targets are automatically reset to PENDING when recurrence triggers
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull CampaignType type,
        @NotNull Long aiAgentId,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalMinutes,
        int maxConcurrentCalls,
        int dailyCallCap,
        RecurrenceType recurrenceType,
        @Min(1) @Max(31) Integer recurringDayOfMonth,
        String cronExpression,
        Boolean autoResetTargets) {

    public RecurrenceType recurrenceTypeOrDefault() {
        return recurrenceType != null ? recurrenceType : RecurrenceType.ONCE;
    }

    public boolean autoResetTargetsOrDefault() {
        return autoResetTargets != null && autoResetTargets;
    }
}
