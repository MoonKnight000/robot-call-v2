package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignType;

/**
 * Agent + numbers + go, in one request ({@code POST /api/campaigns/batch-call}). The same as
 * {@code POST /api/campaigns}, then {@code .../targets/csv}, then {@code .../start}; every
 * campaign setting this leaves out takes its {@code POST /api/campaigns} default.
 *
 * @param csv       the target file, as {@code POST /api/campaigns/{id}/targets/csv} takes it
 * @param schedule  optional; dial window and days default to the company's
 * @param immediate as {@code start?immediate=true}: a future {@code schedule.startAt} is ignored
 */
public record BatchCallRequest(
        @NotBlank String name,
        @NotNull CampaignType type,
        @NotNull Long aiAgentId,
        @NotBlank @Size(max = BatchCallRequest.MAX_CSV_CHARS) String csv,
        @Valid CampaignScheduleDto schedule,
        boolean immediate
) {
    /** The same cap as the CSV upload endpoint. */
    public static final int MAX_CSV_CHARS = 5_000_000;
}
