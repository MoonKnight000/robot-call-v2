package uz.murodjon.robotcallv2.agent.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiChatOptionsStrategyTest {

    private final GeminiChatOptionsStrategy strategy = new GeminiChatOptionsStrategy("minimal");

    private static final GoogleGenAiChatOptions DEFAULTS = GoogleGenAiChatOptions.builder()
            .model("gemini-3.8-flash")
            .maxOutputTokens(1024)
            .thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL)
            .build();

    @Test
    void eachLevelMovesToTheNearestOneTheModelAccepts() {
        // 3.7/3.8 Flash answer 400 to MINIMAL; 3.5/3.6 Flash and Flash-Lite take it; Pro has LOW and HIGH only.
        assertThat(GeminiChatOptionsStrategy.findThinkingLevel(ReasoningEffort.MINIMAL, "gemini-3.8-flash"))
                .isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(GeminiChatOptionsStrategy.findThinkingLevel(ReasoningEffort.MINIMAL, "gemini-3.6-flash"))
                .isEqualTo(GoogleGenAiThinkingLevel.MINIMAL);
        assertThat(GeminiChatOptionsStrategy.findThinkingLevel(ReasoningEffort.NONE, "gemini-3.5-flash-lite"))
                .isEqualTo(GoogleGenAiThinkingLevel.MINIMAL);
        assertThat(GeminiChatOptionsStrategy.findThinkingLevel(ReasoningEffort.MINIMAL, "gemini-3-pro-preview"))
                .isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(GeminiChatOptionsStrategy.findThinkingLevel(ReasoningEffort.MEDIUM, "gemini-3-pro-preview"))
                .isEqualTo(GoogleGenAiThinkingLevel.HIGH);
        assertThat(GeminiChatOptionsStrategy.findThinkingLevel(ReasoningEffort.MEDIUM, "gemini-3.8-flash"))
                .isEqualTo(GoogleGenAiThinkingLevel.MEDIUM);
    }

    @Test
    void gemini3GetsTheCapAndAThinkingLevelButNoSamplingParameters() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("gemini-3.8-flash").maxTokens(300).temperature(0.6)
                .frequencyPenalty(0.3).presencePenalty(0.2)
                .allowedToolNames(List.of("endCall"))
                .build();

        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(options.getMaxTokens()).isEqualTo(300);
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getFrequencyPenalty()).isNull();
        assertThat(options.getPresencePenalty()).isNull();
        // The deployment's "minimal" is moved to what 3.8 Flash takes.
        assertThat(options.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
        assertThat(options.getToolChoice().mode()).isEqualTo(GoogleGenAiChatOptions.ToolChoice.Mode.VALIDATED);
        assertThat(options.getToolChoice().allowedFunctionNames()).containsExactly("endCall");
    }

    @Test
    void theAgentsLevelWinsOverTheDeploymentDefault() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("gemini-3.6-flash").reasoningEffort(ReasoningEffort.HIGH).build();

        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.HIGH);
    }

    @Test
    void gemini25KeepsItsTemperatureAndGetsNoThinkingLevel() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("gemini-2.5-flash").temperature(0.7).reasoningEffort(ReasoningEffort.LOW).build();

        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(options.getThinkingLevel()).isNull();
        assertThat(options.getToolChoice()).isNull();
    }

    @Test
    void anEmptyAllowListAllowsNoTool() {
        PortableChatOptions request = PortableChatOptions.builder()
                .model("gemini-3.8-flash").allowedToolNames(List.of()).build();

        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) strategy.toVendorOptions(DEFAULTS, request);

        assertThat(options.getToolChoice().mode()).isEqualTo(GoogleGenAiChatOptions.ToolChoice.Mode.NONE);
    }
}
