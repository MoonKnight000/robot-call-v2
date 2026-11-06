package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import uz.murodjon.robotcallv2.agent.provider.PortableChatOptions;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    void aTurnCarriesTheAgentsSettingsInNoVendorsShape() {
        // The vendor's shape is RoutingChatModel's job; a vendor type here would lose its own fields
        // in the ChatClient's merge (gpt-6-luna got max_tokens again).
        EffectiveAiModelConfig aiModel = new EffectiveAiModelConfig("gpt-6-luna", 0.6, 120, 300, 100000L)
                .withReasoningEffort(ReasoningEffort.MEDIUM);
        when(session.aiModel()).thenReturn(aiModel);

        ChatOptions options = turnTools.buildOptions(session, List.of());

        assertThat(options).isInstanceOf(PortableChatOptions.class);
        PortableChatOptions portable = (PortableChatOptions) options;
        assertThat(portable.getModel()).isEqualTo("gpt-6-luna");
        assertThat(portable.getMaxTokens()).isEqualTo(120);
        assertThat(portable.getTemperature()).isEqualTo(0.6);
        assertThat(portable.getReasoningEffort()).isEqualTo(ReasoningEffort.MEDIUM);
    }

    @Test
    void withNoAgentLevelTheVendorDefaultIsLeftInPlace() {
        when(session.aiModel()).thenReturn(new EffectiveAiModelConfig("gemini-2.5-flash", 0.7, 100, 300, 100000L));

        PortableChatOptions options = (PortableChatOptions) turnTools.buildOptions(session, List.of());

        assertThat(options.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(options.getTemperature()).isEqualTo(0.7);
        assertThat(options.getMaxTokens()).isEqualTo(100);
        assertThat(options.getReasoningEffort()).isNull();
    }

    @Test
    void onTheCompanysOwnLlmTheTurnRunsOnItsModelNotTheCatalogs() {
        EffectiveAiModelConfig aiModel = new EffectiveAiModelConfig(
                "gemini-3.8-flash", 0.4, 300, 300, 100000L,
                new OwnLlmEndpoint("https://llm.example.uz/v1", "qwen3-32b", "qwen3-8b", "OWN_LLM_KEY")
        );
        when(session.aiModel()).thenReturn(aiModel);

        OpenAiChatOptions main = (OpenAiChatOptions) turnTools.buildOptions(session, List.of());
        OpenAiChatOptions fast = (OpenAiChatOptions) turnTools.buildOptions(session, List.of(), "qwen3-8b");

        assertThat(main.getModel()).isEqualTo("qwen3-32b");
        assertThat(main.getMaxTokens()).isEqualTo(300);
        assertThat(main.getTemperature()).isEqualTo(0.4);
        assertThat(fast.getModel()).isEqualTo("qwen3-8b");
    }

    @Test
    void fallsBackToCascadeModelWhenModelIsRealtime() {
        EffectiveAiModelConfig aiModel = new EffectiveAiModelConfig(
                "gemini-3.8-live-extended-thinking", 0.7, 100, 300, 100000L
        );
        when(session.aiModel()).thenReturn(aiModel);

        ChatOptions options = turnTools.buildOptions(session, List.of());

        assertThat(options.getModel()).isEqualTo("gemini-3.8-flash");
    }
}
