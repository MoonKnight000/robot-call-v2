package uz.murodjon.robotcallv2.aimodel.domain.entity;

/**
 * Merged runtime AI model configuration for a company.
 *
 * @param ownLlm the company's own LLM, or null on the platform's. While set, a call and its summary run on
 *               {@code ownLlm.model()} and {@code model} — the catalog's — only serves what stays on the
 *               platform ({@link #withoutOwnLlm})
 */
public record EffectiveAiModelConfig(
        String model,
        Double temperature,
        Integer maxOutputTokens,
        int maxCallSeconds,
        long maxTokensPerCall,
        OwnLlmEndpoint ownLlm
) {

    /** On the platform's model. */
    public EffectiveAiModelConfig(String model, Double temperature, Integer maxOutputTokens, int maxCallSeconds,
                                  long maxTokensPerCall) {
        this(model, temperature, maxOutputTokens, maxCallSeconds, maxTokensPerCall, null);
    }

    /**
     * The same configuration with a scenario's own model settings laid over it.
     *
     * <p>A company picks one model for everything, and that is the wrong granularity often
     * enough to be worth this: a survey wants a cheap fast model and a collections call wants
     * a careful one, in the same company, on the same day. Only the three tuning fields can
     * be moved — {@code maxCallSeconds} and {@code maxTokensPerCall} are spend limits the
     * company sets, and a scenario raising its own limit would defeat the point of having one.
     *
     * @return {@code this} when nothing was overridden, so the common path allocates nothing
     */
    public EffectiveAiModelConfig withOverrides(String scenarioModel,
                                                Double scenarioTemperature,
                                                Integer scenarioMaxOutputTokens) {
        boolean modelSet = scenarioModel != null && !scenarioModel.isBlank();
        if (!modelSet && scenarioTemperature == null && scenarioMaxOutputTokens == null) {
            return this;
        }
        return new EffectiveAiModelConfig(
                modelSet ? scenarioModel : model,
                scenarioTemperature != null ? scenarioTemperature : temperature,
                scenarioMaxOutputTokens != null ? scenarioMaxOutputTokens : maxOutputTokens,
                maxCallSeconds,
                maxTokensPerCall,
                ownLlm);
    }

    /**
     * The same configuration on the platform's model, for what the company's own LLM does not
     * cover: simulations and the chat widget.
     */
    public EffectiveAiModelConfig withoutOwnLlm() {
        return ownLlm == null ? this
                : new EffectiveAiModelConfig(model, temperature, maxOutputTokens, maxCallSeconds, maxTokensPerCall);
    }

    /** The same configuration with a tighter call-length cap — the balance's, when it runs out sooner. */
    public EffectiveAiModelConfig withMaxCallSeconds(int seconds) {
        return new EffectiveAiModelConfig(model, temperature, maxOutputTokens, seconds, maxTokensPerCall, ownLlm);
    }
}
