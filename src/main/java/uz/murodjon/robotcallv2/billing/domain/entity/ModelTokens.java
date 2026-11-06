package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * What one model spent, before it is priced — a call or a chat turn may run on several models,
 * and each is billed at its own price (billing_model_price).
 *
 * @param model            the model id the provider answered with, or null when unknown (common rate)
 * @param promptTokens     prompt tokens, cached ones included
 * @param completionTokens completion tokens, thinking included
 * @param cachedTokens     of {@code promptTokens}, how many the provider served cached
 */
public record ModelTokens(String model, long promptTokens, long completionTokens, long cachedTokens) {

    /** Prompt tokens billed at the full rate. */
    public long billablePromptTokens() {
        return Math.max(0, promptTokens - cachedTokens);
    }
}
