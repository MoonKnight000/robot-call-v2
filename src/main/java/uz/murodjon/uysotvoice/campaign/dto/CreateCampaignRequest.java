package uz.murodjon.uysotvoice.campaign.dto;

import jakarta.validation.constraints.NotBlank;

import uz.murodjon.uysotvoice.campaign.enums.CampaignType;

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
 */
public record CreateCampaignRequest(
        @NotBlank String name,
        CampaignType type,
        String goalPrompt,
        String defaultLanguage,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd, Set<DayOfWeek> dialDays,
        int maxAttempts, int retryIntervalHours, int maxConcurrentCalls,
        String ttsVoice, int dailyCallCap) {
}
