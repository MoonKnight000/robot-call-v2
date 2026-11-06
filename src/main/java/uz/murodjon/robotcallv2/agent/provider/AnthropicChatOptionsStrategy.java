package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

/**
 * Claude: {@code max_tokens}, a temperature of at most 1, no penalties, and extended thinking as a
 * token budget. Thinking is off unless the agent asks for MEDIUM or HIGH — a voice turn cannot wait
 * for it — and while it is on Claude accepts no temperature and needs {@code max_tokens} above the
 * budget, so the reply keeps its own cap on top of it.
 */
@Component
public class AnthropicChatOptionsStrategy extends AbstractVendorChatOptionsStrategy<AnthropicChatOptions.Builder> {

    /** The smallest budget Claude accepts. */
    static final int MEDIUM_THINKING_BUDGET = 1024;
    static final int HIGH_THINKING_BUDGET = 4096;
    private static final int DEFAULT_REPLY_TOKENS = 1024;

    @Override
    public ChatVendor vendor() {
        return ChatVendor.ANTHROPIC;
    }

    @Override
    protected AnthropicChatOptions.Builder newBuilder() {
        return AnthropicChatOptions.builder();
    }

    @Override
    protected void applyTuning(AnthropicChatOptions.Builder builder, PortableChatOptions request, String model) {
        builder.frequencyPenalty(null).presencePenalty(null);
        Integer budget = findThinkingBudget(request.getReasoningEffort());
        if (budget == null) {
            builder.thinking(null);
            if (request.getMaxTokens() != null) {
                builder.maxTokens(request.getMaxTokens());
            }
            if (request.getTemperature() != null) {
                builder.temperature(Math.min(request.getTemperature(), 1.0));
            }
            return;
        }
        int reply = request.getMaxTokens() != null ? request.getMaxTokens() : DEFAULT_REPLY_TOKENS;
        builder.thinkingEnabled(budget)
                .maxTokens(budget + reply)
                .temperature(null);
    }

    /** @return the thinking budget, or {@code null} for no thinking */
    static Integer findThinkingBudget(ReasoningEffort effort) {
        if (effort == null) {
            return null;
        }
        return switch (effort) {
            case NONE, MINIMAL, LOW -> null;
            case MEDIUM -> MEDIUM_THINKING_BUDGET;
            case HIGH -> HIGH_THINKING_BUDGET;
        };
    }
}
