package uz.murodjon.robotcallv2.aiagent.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import uz.murodjon.robotcallv2.agent.dialog.StudioTestChat;
import uz.murodjon.robotcallv2.agent.dialog.StudioTestTurn;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatMessage;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentTestChatResponse;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.TestChatRole;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.shared.exception.AppException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTestChatServiceTest {

    private static final long COMPANY_ID = 7L;
    private static final long AGENT_ID = 3L;

    private final AiAgentUseCase aiAgentUseCase = mock(AiAgentUseCase.class);
    private final StudioTestChat studioTestChat = mock(StudioTestChat.class);
    private final AgentTestChatService service = new AgentTestChatService(aiAgentUseCase, studioTestChat);
    private final AiAgent agent = AiAgent.builder().id(AGENT_ID).name("Agent").build();

    @BeforeEach
    void setUp() {
        when(aiAgentUseCase.requireAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);
        when(aiAgentUseCase.resolveScenario(COMPANY_ID, agent)).thenReturn(mock(Scenario.class, RETURNS_DEEP_STUBS));
        when(studioTestChat.available()).thenReturn(true);
    }

    @Test
    void anEmptyChatIsOpenedByTheAgent() {
        when(studioTestChat.reply(anyLong(), any(), any(), any(), isNull(), any(), any(), any(), any()))
                .thenReturn(new StudioTestTurn("Assalomu alaykum!", "GREETING", Map.of(), List.of(), List.of(), false, null));

        AgentTestChatResponse response = service.replyTestChat(COMPANY_ID, AGENT_ID,
                new AgentTestChatRequest(List.of(), null, null, null, null, null));

        assertThat(response.reply()).isEqualTo("Assalomu alaykum!");
        assertThat(response.stage()).isEqualTo("GREETING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theConversationSoFarReachesTheTurnInOrder() {
        when(studioTestChat.reply(anyLong(), any(), any(), any(), eq("Qancha?"), any(), any(), any(), any()))
                .thenReturn(new StudioTestTurn("450 000 so'm.", "NEGOTIATE", Map.of(), List.of(), List.of(), false, null));

        service.replyTestChat(COMPANY_ID, AGENT_ID, new AgentTestChatRequest(List.of(
                new AgentTestChatMessage(TestChatRole.AGENT, "Salom"),
                new AgentTestChatMessage(TestChatRole.CLIENT, "Ha")), "  Qancha?  ", "IDENTIFY", null, null, null));

        ArgumentCaptor<List<Message>> history = ArgumentCaptor.forClass(List.class);
        verify(studioTestChat).reply(eq(COMPANY_ID), eq(agent), any(), history.capture(), eq("Qancha?"),
                eq("IDENTIFY"), isNull(), isNull(), isNull());
        assertThat(history.getValue()).hasSize(2);
        assertThat(history.getValue().get(0)).isInstanceOf(AssistantMessage.class);
        assertThat(history.getValue().get(1)).isInstanceOf(UserMessage.class);
    }

    @Test
    void aStartedChatNeedsTheClientsLine() {
        AgentTestChatRequest request = new AgentTestChatRequest(
                List.of(new AgentTestChatMessage(TestChatRole.AGENT, "Salom")), " ", null, null, null, null);

        assertThatThrownBy(() -> service.replyTestChat(COMPANY_ID, AGENT_ID, request))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AGENT_TEST_CHAT_TEXT_REQUIRED));
        verify(studioTestChat, never()).reply(anyLong(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void withoutAModelTheTestIsRefused() {
        when(studioTestChat.available()).thenReturn(false);

        assertThatThrownBy(() -> service.replyTestChat(COMPANY_ID, AGENT_ID,
                new AgentTestChatRequest(List.of(), null, null, null, null, null)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.AGENT_TEST_CHAT_UNAVAILABLE));
    }
}
