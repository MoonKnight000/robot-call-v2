package uz.murodjon.robotcallv2.campaign.application.dto;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignRetry;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryPolicy;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryRule;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Map;

/**
 * {@link CampaignRetry} as the campaign endpoints read and write it.
 *
 * @param intervalMinutes minutes before a client who did not answer (or whose call failed) is
 *                        dialled again; 0 leaves it to the per-disposition defaults, which wait
 *                        longer after a voicemail than after a busy line. A retry lands inside
 *                        the dial window either way
 */
public record CampaignRetryDto(
        int maxAttempts,
        int intervalMinutes,
        Map<Disposition, RetryRule> policy
) {

    public static CampaignRetryDto of(CampaignRetry retry) {
        return new CampaignRetryDto(retry.maxAttempts(), retry.intervalMinutes(), retry.policy().rules());
    }

    public RetryPolicy policyOrNone() {
        return policy == null ? RetryPolicy.NONE : new RetryPolicy(policy);
    }
}
