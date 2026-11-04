package uz.murodjon.robotcallv2.chat.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.agent.summary.SummarizedCall;
import uz.murodjon.robotcallv2.agent.summary.SummaryService;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentWebhookUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatAnalysis;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.chat.infrastructure.config.ChatProperties;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Sentiment;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatAnalysisJobTest {

    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    private final ChatSessionRepository repository = mock(ChatSessionRepository.class);
    private final AiAgentUseCase aiAgentUseCase = mock(AiAgentUseCase.class);
    private final SummaryService summaryService = mock(SummaryService.class);
    private final CallBillingUseCase callBillingUseCase = mock(CallBillingUseCase.class);
    private final AgentWebhookUseCase agentWebhookUseCase = mock(AgentWebhookUseCase.class);
    private final ChatAnalysisJob job = new ChatAnalysisJob(repository, aiAgentUseCase, summaryService,
            callBillingUseCase, new ChatProperties(40, 200, 120), agentWebhookUseCase, mock(ClusterJobLock.class),
            Clock.fixed(NOW, ZoneId.of("UTC")), true);

    private final ChatSession session = new ChatSession(5L, "key", COMPANY_ID, AGENT_ID, 100L, null, null, "uz",
            "GREETING", Map.of(), null, true, 4, NOW.minusSeconds(600), NOW.minusSeconds(60), null);

    @BeforeEach
    void setUp() {
        when(repository.findSessionsToAnalyze(NOW.minusSeconds(120 * 60), 20)).thenReturn(List.of(session));
        AiAgent agent = AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 1L, "uz", null);
        when(aiAgentUseCase.requirePublishedAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);
        when(aiAgentUseCase.resolveScenario(COMPANY_ID, agent)).thenReturn(new Scenario(1L, "s", 1, "S", "", false,
                true, ScenarioDefinition.builder().build(), NOW, 1L));
    }

    @Test
    void aFinishedChatIsSummarisedChargedAndStored() {
        when(repository.findMessages(5L)).thenReturn(List.of(
                new ChatMessage(1L, 5L, ChatRole.AGENT, "Salom!", NOW),
                new ChatMessage(2L, 5L, ChatRole.CLIENT, "3 xonali bormi?", NOW)));
        when(summaryService.summarize(eq(COMPANY_ID), eq("AGENT: Salom!\nCLIENT: 3 xonali bormi?\n"), any(), any(), any()))
                .thenReturn(new SummarizedCall(new CallSummary("Mijoz 3 xonali so'radi", Map.of("rooms", 3),
                        Sentiment.POSITIVE, true, null, null, null, null), 900, 120, 0));

        job.analyzeBatch();

        ArgumentCaptor<ChatAnalysis> analysis = ArgumentCaptor.forClass(ChatAnalysis.class);
        verify(repository).updateAnalysis(eq(5L), analysis.capture());
        assertThat(analysis.getValue().summary()).isEqualTo("Mijoz 3 xonali so'radi");
        assertThat(analysis.getValue().sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(analysis.getValue().outcome()).containsEntry("rooms", 3);
        assertThat(analysis.getValue().analyzedAt()).isEqualTo(NOW);
        verify(callBillingUseCase).chargeChatTurn(eq(COMPANY_ID), eq(5L), eq("analysis"), any());
        verify(agentWebhookUseCase).executeEvent(eq(COMPANY_ID), eq(0L), isNull(), any(), eq(AgentWebhookEvent.CHAT_ENDED), anyMap());
        verify(agentWebhookUseCase).executeEvent(eq(COMPANY_ID), eq(0L), isNull(), any(), eq(AgentWebhookEvent.CHAT_ANALYZED), anyMap());
    }

    @Test
    void aChatWithOnlyTheGreetingIsClosedWithoutSpendingTokens() {
        when(repository.findMessages(5L)).thenReturn(List.of(new ChatMessage(1L, 5L, ChatRole.AGENT, "Salom!", NOW)));

        job.analyzeBatch();

        verify(summaryService, never()).summarize(anyLong(), anyString(), any(), any(), any());
        verify(repository).updateAnalysis(5L, ChatAnalysis.failed(NOW));
    }
}
