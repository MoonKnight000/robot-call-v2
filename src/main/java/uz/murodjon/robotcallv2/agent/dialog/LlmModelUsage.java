package uz.murodjon.robotcallv2.agent.dialog;

/**
 * The tokens one model spent in a call — a call may run on several: the agent's model,
 * its fast model, a fallback when the first one failed. Each is billed at its own price.
 *
 * @param model            the model id the provider answered with ({@code gpt-4o-mini-2024-07-18},
 *                         {@code gemini-2.5-flash}), or null when the response did not say
 * @param promptTokens     prompt tokens, cached ones included
 * @param completionTokens completion tokens, thinking included
 * @param cachedTokens     of {@code promptTokens}, how many were served from the provider's cache
 */
public record LlmModelUsage(String model, long promptTokens, long completionTokens, long cachedTokens) {
}
