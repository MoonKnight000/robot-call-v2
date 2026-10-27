package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * Traffic allocation for one A/B variant calculated by the bandit optimizer.
 *
 * @param variantId              the variant ID
 * @param variantName            the human-readable variant name
 * @param previousTrafficWeight  traffic weight before optimization
 * @param optimizedTrafficWeight newly allocated traffic weight
 * @param answeredCount          number of answered calls so far
 * @param convertedCount         number of conversions so far
 * @param conversionRate         observed conversion rate in percent
 * @param lowerBound             95% Wilson confidence lower bound
 * @param upperBound             95% Wilson confidence upper bound
 */
public record VariantAllocation(
        long variantId,
        String variantName,
        int previousTrafficWeight,
        int optimizedTrafficWeight,
        int answeredCount,
        int convertedCount,
        double conversionRate,
        double lowerBound,
        double upperBound
) {
}
