package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;

/**
 * What one model cost a company for one purpose over a period, summed from llm_usage.
 *
 * @param model   the price-list id, or the reported id when unlisted; null = not known
 * @param listed  whether any of it was priced at the model's own price
 * @param inputUzs  input tokens billed at the full rate, with the markup
 */
public record ModelSpend(
        String model,
        LlmUsagePurpose purpose,
        boolean listed,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long inputUzs,
        long outputUzs,
        long cachedUzs
) {
}
