package uz.murodjon.robotcallv2.conversion.domain.entity;

/**
 * What one A/B variant actually earned: conversions credited to its calls, and what they
 * were worth.
 *
 * <p>Distinct from the campaign's {@code converted_count}, which counts the dispositions
 * the bot recorded — a promise to pay, not a payment. A variant better at extracting
 * promises used to look like the winner on that number alone.
 *
 * @param variantId          the variant, or null for calls of this campaign that ran none
 * @param conversions        events credited to its calls
 * @param attributedValueUzs their total value, 0 when the goal carries no money
 */
public record VariantConversions(Long variantId, long conversions, long attributedValueUzs) {
}
