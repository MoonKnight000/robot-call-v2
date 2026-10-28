package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * How often a client who was not reached is tried again.
 *
 * @param maxAttempts     calls a target gets at most, unless a {@code policy} rule caps it lower
 * @param intervalMinutes minutes before the next try; 0 leaves it to the per-disposition defaults
 * @param policy          per-disposition retry rules; never null, {@link RetryPolicy#NONE} for the defaults
 */
public record CampaignRetry(
        int maxAttempts,
        int intervalMinutes,
        RetryPolicy policy
) {

    public CampaignRetry {
        policy = policy == null ? RetryPolicy.NONE : policy;
    }
}
