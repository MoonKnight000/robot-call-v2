package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TurnToolsTest {

    @Mock
    private DialogProperties dialogProperties;

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private DialogSession session;

    private TurnTools turnTools;

    @BeforeEach
    void setUp() {
        turnTools = new TurnTools(dialogProperties, chatModelProvider);
    }

    @Test
    void returnsOpenAiChatOptionsWhenModelIsGroqLlama() {
        EffectiveAiModelConfig aiModel = new EffectiveAiModelConfig(
                "llama-3.3-70b-versatile", 0.6, 120, 300, 100000L
        );
        when(session.aiModel()).thenReturn(aiModel);

        ChatOptions options = turnTools.buildOptions(session, List.of());

        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        OpenAiChatOptions openAiOptions = (OpenAiChatOptions) options;
        assertThat(openAiOptions.getModel()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(openAiOptions.getTemperature()).isEqualTo(0.6);
        assertThat(openAiOptions.getMaxTokens()).isEqualTo(120);
        assertThat(openAiOptions.getInternalToolExecutionEnabled()).isFalse();
    }

    @Test
    void returnsOpenAiChatOptionsWhenChatModelIsOpenAiChatModel() {
        OpenAiChatModel openAiChatModel = mock(OpenAiChatModel.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(openAiChatModel);

        EffectiveAiModelConfig aiModel = new EffectiveAiModelConfig(
                "custom-model", 0.5, 200, 300, 100000L
        );
        when(session.aiModel()).thenReturn(aiModel);

        ChatOptions options = turnTools.buildOptions(session, List.of());

        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
        OpenAiChatOptions openAiOptions = (OpenAiChatOptions) options;
        assertThat(openAiOptions.getModel()).isEqualTo("custom-model");
        assertThat(openAiOptions.getInternalToolExecutionEnabled()).isFalse();
    }

    @Test
    void returnsGoogleGenAiChatOptionsWhenModelIsGemini() {
        EffectiveAiModelConfig aiModel = new EffectiveAiModelConfig(
                "gemini-3.8-flash", 0.7, 100, 300, 100000L
        );
        when(session.aiModel()).thenReturn(aiModel);

        ChatOptions options = turnTools.buildOptions(session, List.of());

        assertThat(options).isInstanceOf(GoogleGenAiChatOptions.class);
        GoogleGenAiChatOptions googleOptions = (GoogleGenAiChatOptions) options;
        assertThat(googleOptions.getModel()).isEqualTo("gemini-3.8-flash");
        assertThat(googleOptions.getTemperature()).isEqualTo(0.7);
        assertThat(googleOptions.getMaxOutputTokens()).isEqualTo(100);
    }
}
