package uz.murodjon.robotcallv2.agent.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Through a real ChatClient, as a turn goes: its merge into {@code getOptions()} used to drop OpenAI's
 * own fields, so gpt-6-luna was sent max_tokens and no reasoning_effort however the turn was built.
 */
class RoutingChatModelTest {

    private final ChatModelRegistry registry = mock(ChatModelRegistry.class);
    private final GoogleGenAiChatModel gemini = mock(GoogleGenAiChatModel.class);
    private final OpenAiChatModel openAi = mock(OpenAiChatModel.class);
    private ChatClient chatClient;

    @BeforeEach
    void setUp() {
        ChatResponse ok = new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        when(registry.isAvailable(ChatVendor.GOOGLE_GENAI)).thenReturn(true);
        when(registry.isAvailable(ChatVendor.OPENAI)).thenReturn(true);
        when(registry.findByVendor(ChatVendor.GOOGLE_GENAI)).thenReturn(gemini);
        when(registry.findByVendor(ChatVendor.OPENAI)).thenReturn(openAi);
        when(gemini.getOptions()).thenReturn(GoogleGenAiChatOptions.builder()
                .model("gemini-3.8-flash").maxOutputTokens(1024).thinkingLevel(GoogleGenAiThinkingLevel.LOW).build());
        when(openAi.getOptions()).thenReturn(OpenAiChatOptions.builder()
                .model("gpt-5.6-luna").maxCompletionTokens(1024).build());
        when(gemini.call(any(Prompt.class))).thenReturn(ok);
        when(openAi.call(any(Prompt.class))).thenReturn(ok);
        VendorChatOptionsStrategies strategies = new VendorChatOptionsStrategies(List.of(
                new GeminiChatOptionsStrategy("LOW"), new OpenAiChatOptionsStrategy("low"),
                new AnthropicChatOptionsStrategy()));
        chatClient = ChatClient.builder(new RoutingChatModel(registry, strategies, ChatVendor.GOOGLE_GENAI)).build();
    }

    @Test
    void aTurnOnGpt6ReachesOpenAiInOpenAisShape() {
        chatClient.prompt()
                .options(PortableChatOptions.builder()
                        .model("gpt-6-luna").maxTokens(120).temperature(0.6).frequencyPenalty(0.3)
                        .reasoningEffort(ReasoningEffort.MEDIUM))
                .user("salom")
                .call()
                .content();

        OpenAiChatOptions sent = (OpenAiChatOptions) sentTo(openAi).getOptions();
        assertThat(sent.getModel()).isEqualTo("gpt-6-luna");
        assertThat(sent.getMaxCompletionTokens()).isEqualTo(120);
        assertThat(sent.getMaxTokens()).isNull();
        assertThat(sent.getTemperature()).isNull();
        assertThat(sent.getFrequencyPenalty()).isNull();
        assertThat(sent.getReasoningEffort()).isEqualTo("medium");
    }

    @Test
    void aCallerThatStillBuildsGeminiOptionsKeepsItsThinkingLevel() {
        chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder()
                        .model("gemini-3.8-flash").thinkingLevel(GoogleGenAiThinkingLevel.MINIMAL))
                .user("salom")
                .call()
                .content();

        GoogleGenAiChatOptions sent = (GoogleGenAiChatOptions) sentTo(gemini).getOptions();
        // Carried over as a level, then moved to what 3.8 Flash accepts.
        assertThat(sent.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
    }

    private static Prompt sentTo(ChatModel model) {
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        return prompt.getValue();
    }
}
