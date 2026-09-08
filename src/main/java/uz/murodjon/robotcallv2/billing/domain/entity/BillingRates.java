package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * The price list a call is charged against, in UZS.
 *
 * <p>A domain record rather than the configuration class that carries it, so that
 * {@code CallCostCalculator} — the one piece of billing a company will argue with — stays
 * a function of two records and can be tested by handing it numbers. The binding to
 * {@code config/billing.yml} lives in {@code BillingRateProperties}, which composes this.
 *
 * @param llm                     token prices
 * @param sttPerMinuteUzs         recognition, priced on the call's duration
 * @param ttsPer1kCharsUzs        synthesis, priced on the characters the agent spoke
 * @param telephonyPerMinuteUzs   what the trunk costs per connected minute
 * @param platformFeePerMinuteUzs this platform's own charge; carries no markup
 * @param markupBasisPoints       applied to the provider costs only; 2000 = 20%
 */
public record BillingRates(
        BillingLlmRates llm,
        long sttPerMinuteUzs,
        long ttsPer1kCharsUzs,
        long telephonyPerMinuteUzs,
        long platformFeePerMinuteUzs,
        int markupBasisPoints
) {
}
