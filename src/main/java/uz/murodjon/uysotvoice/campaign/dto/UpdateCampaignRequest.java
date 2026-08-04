package uz.murodjon.uysotvoice.campaign.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/**
 * Full edit of a campaign's configuration ({@code PUT /api/campaigns/{id}}) — the same
 * shape as {@link CreateCampaignRequest}, minus the fields that are only meaningful at
 * creation time (type, script config).
 *
 * @param dialDays     weekdays this campaign may dial on ({@code ["MONDAY", ...]})
 * @param ttsVoice     id of a voice from {@code GET /api/tts/voices} (§2.5); an unknown id
 *                     is rejected
 * @param dailyCallCap most calls this campaign may place in one day; 0 for unlimited
 * @param disclosureEnabled whether calls open with the §11.1 disclosure ("Assalomu
 *                     alaykum! Bu Uysot kompaniyasining avtomatik ovozli xizmati...")
 */
public record UpdateCampaignRequest(
        @NotBlank String name,
        String goalPrompt,
        String defaultLanguage,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts, int retryIntervalHours, int maxConcurrentCalls,
        String ttsVoice, int dailyCallCap,
        boolean disclosureEnabled) {
}
