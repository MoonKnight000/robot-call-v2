package uz.murodjon.robotcallv2.agent.summary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Post-call summary settings. Bound from {@code voice-agent.summary.*}.
 *
 * @param enabled         when false no summary is generated after a call
 * @param model           chat model that writes the summary
 * @param reasoningEffort how much the model may think before answering
 * @param maxTokens       output cap for one summary
 */
@ConfigurationProperties(prefix = "voice-agent.summary")
public record SummaryProperties(
        Boolean enabled,
        String model,
        String reasoningEffort,
        int maxTokens
) {

    public SummaryProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-flash";
        }
        if (reasoningEffort == null || reasoningEffort.isBlank()) {
            reasoningEffort = "low";
        }
        if (maxTokens <= 0) {
            maxTokens = 2048;
        }
    }
}
