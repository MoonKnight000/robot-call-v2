package uz.murodjon.robotcallv2.billing.application.dto;

import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;

import java.util.Map;

/**
 * One model's LLM cost over the report's period (docs/api/billing.md §2a).
 *
 * @param model        the price-list id, or the reported id when unlisted; null = not known (common rate)
 * @param listed       true when it was priced at its own price, false = the common rate
 * @param totalUzs     input + output + cached
 * @param uzsByPurpose the same total split by what the tokens were for; only purposes with spend
 */
public record ModelCostDto(
        String model,
        boolean listed,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long inputUzs,
        long outputUzs,
        long cachedUzs,
        long totalUzs,
        Map<LlmUsagePurpose, Long> uzsByPurpose
) {
}
