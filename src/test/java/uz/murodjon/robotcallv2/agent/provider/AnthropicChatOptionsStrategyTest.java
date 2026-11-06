package uz.murodjon.robotcallv2.agent.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicChatOptionsStrategyTest {

    private final AnthropicChatOptionsStrategy strategy = new AnthropicChatOptionsStrategy();

    private static final AnthropicChatOptions DEFAULTS = AnthropicChatOptions.builder()
            .model("claude-haiku-5-5")
            .maxTokens(1024)
            .build();

    @Test
    void withoutThinkingClaudeGetsItsCapAndATemperatureItAccepts() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("claude-haiku-5-5").maxTokens(200).temperature(1.5)
                .frequencyPenalty(0.3).reasoningEffort(ReasoningEffort.LOW)
                .build();

        AnthropicChatOptions options = (AnthropicChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getModel()).isEqualTo("claude-haiku-5-5");
        assertThat(options.getMaxTokens()).isEqualTo(200);
        assertThat(options.getTemperature()).isEqualTo(1.0);
        assertThat(options.getFrequencyPenalty()).isNull();
        assertThat(options.getThinking()).isNull();
    }

    @Test
    void highThinkingIsABudgetOnTopOfTheReplyAndDropsTheTemperature() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("claude-haiku-5-5").maxTokens(200).temperature(0.6)
                .reasoningEffort(ReasoningEffort.HIGH)
                .build();

        AnthropicChatOptions options = (AnthropicChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getThinking().isEnabled()).isTrue();
        assertThat(options.getThinking().asEnabled().budgetTokens())
                .isEqualTo((long) AnthropicChatOptionsStrategy.HIGH_THINKING_BUDGET);
        assertThat(options.getMaxTokens()).isEqualTo(AnthropicChatOptionsStrategy.HIGH_THINKING_BUDGET + 200);
        assertThat(options.getTemperature()).isNull();
    }
}
