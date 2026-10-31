package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Agent + audience + start, in one request ({@code POST /api/campaigns/batch-call}).
 * Accepts targets as CSV, as a tool-based dynamic query, or as a direct list of target objects.
 *
 * @param csv        the target file, as {@code POST /api/campaigns/{id}/targets/csv} takes it
 * @param toolSource an integration tool to run to fetch the audience
 * @param targets    direct JSON target objects with dynamic variables
 * @param schedule   optional; dial window and days default to the company's
 * @param limits     optional concurrency and spend limits
 * @param retry      optional retry policy
 * @param immediate  as {@code start?immediate=true}: a future {@code schedule.startAt} is ignored
 * @param consentConfirmed the caller confirms the legal consent for the contacts being called, as
 *                  {@code POST /campaigns/{id}/consent} does — a launch is refused without it
 */
public record BatchCallRequest(
        @NotBlank String name,
        @NotNull Long aiAgentId,
        @Size(max = BatchCallRequest.MAX_CSV_CHARS) String csv,
        @Valid BatchCallToolSourceDto toolSource,
        @Valid List<BatchCallTargetItemDto> targets,
        @Valid CampaignScheduleDto schedule,
        @Valid CampaignLimitsDto limits,
        @Valid CampaignRetryDto retry,
        boolean immediate,
        boolean consentConfirmed
) {
    /** The same cap as the CSV upload endpoint. */
    public static final int MAX_CSV_CHARS = 5_000_000;
}
