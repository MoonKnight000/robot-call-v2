package uz.murodjon.robotcallv2.pack.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The pack generator (UNIVERSAL-OPERATOR-PLAN E.1). Bound from {@code voice-agent.pack-generator.*}.
 *
 * @param model     the chat model that writes the draft. A draft is read by a person before
 *                  anything is created, but it is also long and structured — a small model
 *                  writes stages that do not hold together
 * @param maxTokens output cap for one draft; a pack with stages, guardrails and five test
 *                  cases is several thousand tokens of JSON
 */
@ConfigurationProperties(prefix = "voice-agent.pack-generator")
public record PackGeneratorProperties(
        String model,
        int maxTokens
) {

    public PackGeneratorProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-pro";
        }
        if (maxTokens <= 0) {
            maxTokens = 8192;
        }
    }
}
