package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * One model's share of the LLM line, priced. The three amounts are rounded on their own and
 * include the markup; the LLM line of {@link CallCostBreakdown} is their sum over every model.
 *
 * @param model            the price-list id the tokens were priced as, or the reported id
 *                         (normalized) when the list has no row for it; null when unknown
 * @param listed           whether the model's own price was used — false means the common rate
 * @param tokens           what it spent
 * @param rates            the prices used, per million tokens, before the markup
 * @param promptUzs        input (prompt) tokens at the full rate
 * @param completionUzs    output (completion) tokens
 * @param cachedUzs        cached prompt tokens
 */
public record ModelLlmCost(
        String model,
        boolean listed,
        ModelTokens tokens,
        BillingLlmRates rates,
        long promptUzs,
        long completionUzs,
        long cachedUzs
) {
}
