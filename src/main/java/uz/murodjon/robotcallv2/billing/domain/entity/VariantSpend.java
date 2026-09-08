package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What one A/B variant's calls have cost so far.
 *
 * <p>The other half of judging a variant: a script that converts slightly better while
 * talking twice as long is not the better script, and until a campaign's charges could be
 * split by variant there was no way to see that.
 *
 * @param variantId  the variant, or null for the campaign's calls that ran no variant
 * @param calls      settled calls behind the figure
 * @param spentUzs   what they were charged
 */
public record VariantSpend(Long variantId, long calls, long spentUzs) {
}
