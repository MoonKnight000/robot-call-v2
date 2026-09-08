package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What a million LLM tokens costs, in UZS.
 *
 * <p>Cached prompt tokens are a separate line because they are a large share of a voice
 * call's prompt — the stable prefix is re-sent every turn — and pricing them at the full
 * prompt rate would charge a company several times over for the same system prompt.
 *
 * @param promptPerMillionUzs       prompt tokens billed at the full rate
 * @param completionPerMillionUzs   tokens the model wrote
 * @param cachedPromptPerMillionUzs prompt tokens the provider served from its cache
 */
public record BillingLlmRates(
        long promptPerMillionUzs,
        long completionPerMillionUzs,
        long cachedPromptPerMillionUzs
) {
}
