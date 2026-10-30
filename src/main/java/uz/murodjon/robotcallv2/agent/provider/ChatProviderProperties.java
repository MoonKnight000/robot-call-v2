package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which text-LLM vendors this deployment can reach. Bound from {@code voice-agent.llm.*}.
 *
 * <p>A vendor is offered — in the model catalog and to the router — exactly when its key is
 * set, so an operator picks among whatever the deployment was given keys for and never
 * saves a model that cannot answer.
 *
 * @param defaultProvider the vendor a request that names no model (or an unknown one) runs on;
 *                        also the one Spring AI auto-configures ({@code spring.ai.model.chat})
 * @param geminiApiKey    Gemini Developer API key; blank = Gemini is not offered
 * @param openAiApiKey    OpenAI key; blank = OpenAI is not offered
 * @param openAiBaseUrl   OpenAI-compatible endpoint behind the OpenAI slot
 * @param anthropicApiKey Anthropic key; blank = Claude is not offered
 */
@ConfigurationProperties(prefix = "voice-agent.llm")
public record ChatProviderProperties(
        String defaultProvider,
        String geminiApiKey,
        String openAiApiKey,
        String openAiBaseUrl,
        String anthropicApiKey
) {
}
