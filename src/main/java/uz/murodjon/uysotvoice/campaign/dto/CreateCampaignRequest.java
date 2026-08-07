package uz.murodjon.uysotvoice.campaign.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.campaign.enums.CampaignType;
import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
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
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        @NotNull CampaignType type,
        String goalPrompt,
        String defaultLanguage,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd, Set<DayOfWeek> dialDays,
        int maxAttempts, int retryIntervalMinutes, int maxConcurrentCalls,
        String ttsVoice, int dailyCallCap,
        @NotNull Long scenarioId,
        Boolean disclosureEnabled) {
}
