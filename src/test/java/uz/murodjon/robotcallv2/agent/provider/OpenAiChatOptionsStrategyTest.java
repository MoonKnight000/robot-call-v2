package uz.murodjon.robotcallv2.agent.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiChatOptionsStrategyTest {

    private final OpenAiChatOptionsStrategy strategy = new OpenAiChatOptionsStrategy("low");

    private static final OpenAiChatOptions DEFAULTS = OpenAiChatOptions.builder()
            .model("gpt-5.6-luna")
            .maxCompletionTokens(1024)
            .temperature(0.7)
            .build();

    @Test
    void aReasoningModelGetsOnlyWhatItAccepts() {
        // gpt-5/6 answer 400 to max_tokens, to a temperature other than 1 and to the penalties.
        PortableChatOptions request = PortableChatOptions.builder()
                .model("gpt-6-luna").maxTokens(120).temperature(0.6)
                .frequencyPenalty(0.3).presencePenalty(0.2)
                .build();

        OpenAiChatOptions options = (OpenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getModel()).isEqualTo("gpt-6-luna");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(120);
        assertThat(options.getMaxTokens()).isNull();
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getFrequencyPenalty()).isNull();
        assertThat(options.getPresencePenalty()).isNull();
        assertThat(options.getReasoningEffort()).isEqualTo("low");
    }

    @Test
    void theAgentsLevelIsSentInOpenAisTermsAndMovedToWhatTheModelTakes() {
        assertThat(OpenAiChatOptionsStrategy.findReasoningEffort(ReasoningEffort.HIGH, "gpt-6-luna")).isEqualTo("high");
        assertThat(OpenAiChatOptionsStrategy.findReasoningEffort(ReasoningEffort.MINIMAL, "gpt-6-luna")).isEqualTo("low");
        assertThat(OpenAiChatOptionsStrategy.findReasoningEffort(ReasoningEffort.NONE, "gpt-5.6-luna")).isEqualTo("none");

        PortableChatOptions request = PortableChatOptions.builder()
                .model("gpt-6-luna").reasoningEffort(ReasoningEffort.MEDIUM).build();
        OpenAiChatOptions options = (OpenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getReasoningEffort()).isEqualTo("medium");
        // No cap in the request: the model's own default cap, in the field the model reads.
        assertThat(options.getMaxCompletionTokens()).isEqualTo(1024);
    }

    @Test
    void gpt6WithToolsIsSentNoReasoningBecauseChatCompletionsRefusesBoth() {
        // 400: "Function tools with reasoning_effort are not supported for gpt-6-luna in /v1/chat/completions".
        PortableChatOptions withTools = PortableChatOptions.builder()
                .model("gpt-6-luna").reasoningEffort(ReasoningEffort.HIGH)
                .toolCallbacks(List.of(mock(ToolCallback.class)))
                .build();
        PortableChatOptions withoutTools = PortableChatOptions.builder()
                .model("gpt-6-luna").reasoningEffort(ReasoningEffort.HIGH).build();

        assertThat(((OpenAiChatOptions) strategy.toVendorOptions(DEFAULTS, withTools)).getReasoningEffort())
                .isEqualTo("none");
        assertThat(((OpenAiChatOptions) strategy.toVendorOptions(DEFAULTS, withoutTools)).getReasoningEffort())
                .isEqualTo("high");
    }

    @Test
    void aClassicModelKeepsTheClassicFieldsAndNoReasoningEffort() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("llama-3.3-70b").maxTokens(200).temperature(0.5)
                .frequencyPenalty(0.3).reasoningEffort(ReasoningEffort.HIGH)
                .build();

        OpenAiChatOptions options = (OpenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getMaxTokens()).isEqualTo(200);
        assertThat(options.getMaxCompletionTokens()).isNull();
        assertThat(options.getTemperature()).isEqualTo(0.5);
        assertThat(options.getFrequencyPenalty()).isEqualTo(0.3);
        assertThat(options.getReasoningEffort()).isNull();
    }
}
