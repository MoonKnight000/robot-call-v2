package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;
import java.util.Locale;

/**
 * Gemini: {@code maxOutputTokens}, {@code thinkingLevel} on Gemini 3, no sampling parameters
 * on Gemini 3, and the turn's allowed tools as a validated {@code toolChoice}.
 */
@Component
public class GeminiChatOptionsStrategy extends AbstractVendorChatOptionsStrategy<GoogleGenAiChatOptions.Builder> {

    /** The deployment's level (spring.ai.google.genai.chat.thinking-level) for an agent that sets none. */
    private final ReasoningEffort defaultEffort;

    public GeminiChatOptionsStrategy(@Value("${spring.ai.google.genai.chat.thinking-level:LOW}") String defaultEffort) {
        this.defaultEffort = ReasoningEffort.parse(defaultEffort);
    }

    @Override
    public ChatVendor vendor() {
        return ChatVendor.GOOGLE_GENAI;
    }

    @Override
    protected GoogleGenAiChatOptions.Builder newBuilder() {
        return GoogleGenAiChatOptions.builder();
    }

    @Override
    protected void applyTuning(GoogleGenAiChatOptions.Builder builder, PortableChatOptions request, String model) {
        boolean gemini3 = isGemini3OrLater(model);
        if (request.getMaxTokens() != null) {
            builder.maxOutputTokens(request.getMaxTokens());
        }
        // Gemini 3 deprecates the sampling parameters; the penalties were never sent to Gemini.
        if (gemini3) {
            builder.temperature(null);
        } else if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        builder.frequencyPenalty(null).presencePenalty(null);
        ReasoningEffort effort = request.getReasoningEffort() != null ? request.getReasoningEffort() : defaultEffort;
        // Gemini 2.5 rejects thinkingLevel outright (it reads thinkingBudget instead).
        builder.thinkingLevel(gemini3 && effort != null ? findThinkingLevel(effort, model) : null);
        GoogleGenAiChatOptions.ToolChoice toolChoice = findToolChoice(request.getAllowedToolNames());
        if (toolChoice != null) {
            builder.toolChoice(toolChoice);
        }
    }

    /**
     * The nearest level the model accepts. Measured on 2026-11-05/06: 3.7 and 3.8 Flash answer 400 to
     * MINIMAL while 3.5/3.6 Flash and Flash-Lite take it; the Pro models take only LOW and HIGH.
     * No Gemini 3 model can switch thinking off, so NONE is the lightest level it has.
     */
    static GoogleGenAiThinkingLevel findThinkingLevel(ReasoningEffort effort, String model) {
        String id = model == null ? "" : model.toLowerCase(Locale.ROOT);
        boolean pro = id.contains("-pro");
        boolean takesMinimal = !pro && (id.contains("lite") || id.startsWith("gemini-3.5") || id.startsWith("gemini-3.6"));
        return switch (effort) {
            case NONE, MINIMAL -> takesMinimal ? GoogleGenAiThinkingLevel.MINIMAL : GoogleGenAiThinkingLevel.LOW;
            case LOW -> GoogleGenAiThinkingLevel.LOW;
            case MEDIUM -> pro ? GoogleGenAiThinkingLevel.HIGH : GoogleGenAiThinkingLevel.MEDIUM;
            case HIGH -> GoogleGenAiThinkingLevel.HIGH;
        };
    }

    /** A blank model is the default one, gemini-3.8-flash. */
    static boolean isGemini3OrLater(String model) {
        return model == null || model.isBlank() || model.toLowerCase(Locale.ROOT).contains("gemini-3");
    }

    private static GoogleGenAiChatOptions.ToolChoice findToolChoice(List<String> allowedToolNames) {
        if (allowedToolNames == null) {
            return null;
        }
        return allowedToolNames.isEmpty()
                ? GoogleGenAiChatOptions.ToolChoice.builder().mode(GoogleGenAiChatOptions.ToolChoice.Mode.NONE).build()
                : GoogleGenAiChatOptions.ToolChoice.builder()
                        .mode(GoogleGenAiChatOptions.ToolChoice.Mode.VALIDATED)
                        .allowedFunctionNames(allowedToolNames)
                        .build();
    }
}
