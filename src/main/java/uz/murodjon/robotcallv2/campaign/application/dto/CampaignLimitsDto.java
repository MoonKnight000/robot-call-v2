package uz.murodjon.robotcallv2.campaign.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignLimits;

/**
 * {@link CampaignLimits} as the campaign endpoints read and write it.
 *
 * @param dailyCallCap most calls this campaign may place in one day; 0 or omitted for
 *                     unlimited. A spend ceiling — every call costs STT, LLM, TTS and trunk
 *                     minutes, and a campaign with 50 000 targets will spend them all
 * @param maxCps       new calls it may start per second; omitted for the SIP trunks' full CPS
 */
public record CampaignLimitsDto(
        int maxConcurrentCalls,
        int dailyCallCap,
        @Min(1) Long budgetUzs,
        @Min(1) Long dailyBudgetUzs,
        @Min(1) @Max(100) Integer maxCps
) {

    public static CampaignLimitsDto of(CampaignLimits limits) {
        return new CampaignLimitsDto(limits.maxConcurrentCalls(), limits.dailyCallCap(), limits.budgetUzs(),
                limits.dailyBudgetUzs(), limits.maxCps());
    }
}
