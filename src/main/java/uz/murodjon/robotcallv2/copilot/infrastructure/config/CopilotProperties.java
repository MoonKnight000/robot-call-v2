package uz.murodjon.robotcallv2.copilot.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The panel copilot. Bound from {@code voice-agent.copilot.*}.
 *
 * @param model        the chat model; it plans tool calls over several rounds, so a small one loses the thread
 * @param maxTokens    output cap for one round
 * @param maxToolCalls tool calls one question may make; past it the model is told to answer with what it has
 * @param maxHistory   earlier questions and answers sent along with a new question
 */
@ConfigurationProperties(prefix = "voice-agent.copilot")
public record CopilotProperties(
        String model,
        int maxTokens,
        int maxToolCalls,
        int maxHistory
) {

    public CopilotProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-3.8-flash";
        }
        if (maxTokens <= 0) {
            maxTokens = 4096;
        }
        if (maxToolCalls <= 0) {
            maxToolCalls = 15;
        }
        if (maxHistory <= 0) {
            maxHistory = 20;
        }
    }
}
