package uz.murodjon.robotcallv2.agent.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM-as-judge call-quality scoring. Bound from {@code voice-agent.quality.*}.
 *
 * @param enabled    when false no call is scored
 * @param sampleRate share of finished calls to score, 0..1
 * @param model      chat model that does the scoring
 * @param maxTokens  output cap for one verdict
 */
@ConfigurationProperties(prefix = "voice-agent.quality")
public record QualityProperties(
        boolean enabled,
        double sampleRate,
        String model,
        int maxTokens
) {

    public QualityProperties {
        if (sampleRate <= 0) {
            sampleRate = 1.0;
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-flash";
        }
        if (maxTokens <= 0) {
            maxTokens = 512;
        }
    }
}
