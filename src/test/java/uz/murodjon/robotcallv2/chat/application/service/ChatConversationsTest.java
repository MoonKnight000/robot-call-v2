package uz.murodjon.robotcallv2.chat.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.agent.dialog.DialogSession;
import uz.murodjon.robotcallv2.agent.dialog.TextTurn;
import uz.murodjon.robotcallv2.agent.dialog.TextTurnEngine;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentScript;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatQuotaPort;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatSessionRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatTurn;
import uz.murodjon.robotcallv2.chat.domain.entity.StartedChat;
import uz.murodjon.robotcallv2.chat.domain.enums.ChatRole;
import uz.murodjon.robotcallv2.chat.infrastructure.config.ChatProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.domain.entity.RememberedCall;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatConversationsTest {

    private static final long COMPANY_ID = 1L;
    private static final long AGENT_ID = 10L;
    private static final String PHONE = "+998901234567";
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private ChatSessionRepository repository;
    private ChatQuotaPort chatQuota;
    private AiAgentUseCase aiAgentUseCase;
    private ToolUseCase toolUseCase;
    private AiModelConfigUseCase aiModelConfigUseCase;
    private CompanyUseCase companyUseCase;
    private ClientMemoryUseCase clientMemoryUseCase;
    private TextTurnEngine textTurnEngine;
    private ChatProperties chatProperties;
    private Clock clock;

    private ChatConversations conversations;
    private AiAgent agent;
    private Scenario scenario;

    @BeforeEach
    void setUp() {
        repository = mock(ChatSessionRepository.class);
        chatQuota = mock(ChatQuotaPort.class);
        aiAgentUseCase = mock(AiAgentUseCase.class);
        toolUseCase = mock(ToolUseCase.class);
        aiModelConfigUseCase = mock(AiModelConfigUseCase.class);
        companyUseCase = mock(CompanyUseCase.class);
        clientMemoryUseCase = mock(ClientMemoryUseCase.class);
        textTurnEngine = mock(TextTurnEngine.class);
        chatProperties = new ChatProperties(40, 200, 120);
        clock = Clock.fixed(NOW, ZoneId.of("UTC"));

        conversations = new ChatConversations(
                repository, chatQuota, aiAgentUseCase, toolUseCase,
                aiModelConfigUseCase, companyUseCase, clientMemoryUseCase,
                textTurnEngine, chatProperties, clock);

        agent = AiAgentFixtures.agent(AGENT_ID, COMPANY_ID, 1L, "uz", "yandex:zahar");
        ScenarioDefinition def = ScenarioDefinition.builder()
                .stages(List.of(new StageDef("GREETING", "Salomlash", List.of("ORDER"), List.of())))
                .build();
        scenario = new Scenario(1L, "support-scenario", 1, "Support", "Desc", false, true, def, NOW, 1L);

        when(aiAgentUseCase.requirePublishedAgent(COMPANY_ID, AGENT_ID)).thenReturn(agent);
        when(aiAgentUseCase.resolveScenario(COMPANY_ID, agent)).thenReturn(scenario);
        when(chatQuota.bookSession(eq(AGENT_ID), anyInt())).thenReturn(true);
        when(companyUseCase.findById(COMPANY_ID)).thenReturn(Company.named("Test Company"));
        when(aiModelConfigUseCase.findEffectiveByCompanyId(COMPANY_ID))
                .thenReturn(new EffectiveAiModelConfig("gemini-2.5", 0.7, 1000, 600, 100000));
        when(repository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void startChatWithoutPhone_buildsSessionWithDefaultLanguageAndNoMemory() {
        when(textTurnEngine.reply(any(DialogSession.class), anyString()))
                .thenReturn(new TextTurn("Assalomu alaykum! Qanday yordam bera olaman?", List.of()));

        ChatSession session = ChatSession.startingOnWidget("key-1", COMPANY_ID, AGENT_ID, 100L, null, "GREETING");
        StartedChat started = conversations.start(session);

        assertThat(started).isNotNull();
        assertThat(started.greeting()).isEqualTo("Assalomu alaykum! Qanday yordam bera olaman?");
        assertThat(started.session().language()).isEqualTo("uz");
        verify(clientMemoryUseCase, never()).findByCompanyIdAndPhone(anyLong(), anyString());
    }

    @Test
    void startChatWithPhoneAndMemory_adoptsPreferredLanguageAndProvidesMemory() {
        when(textTurnEngine.reply(any(DialogSession.class), anyString()))
                .thenReturn(new TextTurn("Здравствуйте! Чем могу помочь?", List.of()));

        ClientMemory memory = new ClientMemory(1L, COMPANY_ID, PHONE, "Aziz aka", "ru",
                "VIP mijoz", List.of(), Map.of("vip", true), NOW);
        when(clientMemoryUseCase.findByCompanyIdAndPhone(COMPANY_ID, PHONE)).thenReturn(memory);

        ChatSession session = new ChatSession(1L, "key-2", COMPANY_ID, AGENT_ID, 100L, null, null,
                null, "GREETING", Map.of("phone", PHONE), null, false, 0, NOW, NOW);

        StartedChat started = conversations.start(session);

        assertThat(started).isNotNull();
        assertThat(started.greeting()).isEqualTo("Здравствуйте! Чем могу помочь?");
        assertThat(started.session().language()).isEqualTo("ru");
        verify(clientMemoryUseCase).findByCompanyIdAndPhone(COMPANY_ID, PHONE);
    }

    @Test
    void startChatWithFirstMessageTemplate_rendersClientPreferredName() {
        AiAgent templatedAgent = agent.toBuilder()
                .script(new AiAgentScript("Salom, {{clientName | hurmatli mijoz}}! Sizga qanday yordam bera olaman?",
                        1L, ScenarioMode.PROMPT, null, null, null))
                .build();
        when(aiAgentUseCase.requirePublishedAgent(COMPANY_ID, AGENT_ID)).thenReturn(templatedAgent);
        when(aiAgentUseCase.resolveScenario(COMPANY_ID, templatedAgent)).thenReturn(scenario);

        ClientMemory memory = new ClientMemory(1L, COMPANY_ID, PHONE, "Murodjon aka", "uz",
                null, List.of(), Map.of(), NOW);
        when(clientMemoryUseCase.findByCompanyIdAndPhone(COMPANY_ID, PHONE)).thenReturn(memory);

        ChatSession session = new ChatSession(1L, "key-3", COMPANY_ID, AGENT_ID, 100L, null, null,
                "uz", "GREETING", Map.of("clientPhone", PHONE), null, false, 0, NOW, NOW);

        StartedChat started = conversations.start(session);

        assertThat(started.greeting()).isEqualTo("Salom, Murodjon aka! Sizga qanday yordam bera olaman?");
        verify(clientMemoryUseCase).findByCompanyIdAndPhone(COMPANY_ID, PHONE);
    }

    @Test
    void answerChat_rebuildsWithClientMemoryAndAnswersMessage() {
        ClientMemory memory = new ClientMemory(1L, COMPANY_ID, PHONE, "Karim aka", "uz",
                null, List.of(), Map.of(), NOW);
        when(clientMemoryUseCase.findByCompanyIdAndPhone(COMPANY_ID, PHONE)).thenReturn(memory);
        when(repository.findMessages(1L)).thenReturn(List.of(
                ChatMessage.written(1L, ChatRole.AGENT, "Assalomu alaykum!")));
        when(textTurnEngine.reply(any(DialogSession.class), eq("Buyurtmam qayerda?")))
                .thenReturn(new TextTurn("Buyurtmangiz yo'lda, Karim aka.", List.of()));

        ChatSession session = new ChatSession(1L, "key-4", COMPANY_ID, AGENT_ID, 100L, null, null,
                "uz", "ORDER", Map.of("phone", PHONE), null, false, 1, NOW, NOW);

        ChatTurn turn = conversations.answer(session, "Buyurtmam qayerda?");

        assertThat(turn.reply()).isEqualTo("Buyurtmangiz yo'lda, Karim aka.");
        verify(clientMemoryUseCase).findByCompanyIdAndPhone(COMPANY_ID, PHONE);
        verify(clientMemoryUseCase, never()).rememberCall(anyLong(), anyString(), any(), any());
    }

    @Test
    void answerChat_whenSessionEnds_recordsRememberedCallIntoClientMemory() {
        ClientMemory memory = new ClientMemory(1L, COMPANY_ID, PHONE, "Karim aka", "uz",
                null, List.of(), Map.of(), NOW);
        when(clientMemoryUseCase.findByCompanyIdAndPhone(COMPANY_ID, PHONE)).thenReturn(memory);
        when(repository.findMessages(1L)).thenReturn(List.of());

        when(textTurnEngine.reply(any(DialogSession.class), anyString())).thenAnswer(inv -> {
            DialogSession ds = inv.getArgument(0);
            ds.setDisposition(Disposition.SUCCESSFUL);
            ds.setEnded(true);
            ds.recordOutcome("orderId", "ORD-12345");
            ds.recordOutcome("summary", "Mijoz buyurtmasi muvaffaqiyatli yetkazildi.");
            return new TextTurn("Rahmat, suhbatingiz uchun!", List.of());
        });

        ChatSession session = new ChatSession(1L, "key-5", COMPANY_ID, AGENT_ID, 100L, null, null,
                "uz", "ORDER", Map.of("phone", PHONE), null, false, 2, NOW, NOW);

        ChatTurn turn = conversations.answer(session, "Hammasi tushunarli, rahmat!");

        assertThat(turn.session().ended()).isTrue();
        assertThat(turn.session().disposition()).isEqualTo(Disposition.SUCCESSFUL);

        ArgumentCaptor<RememberedCall> callCaptor = ArgumentCaptor.forClass(RememberedCall.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> factsCaptor = ArgumentCaptor.forClass(Map.class);

        verify(clientMemoryUseCase).rememberCall(eq(COMPANY_ID), eq(PHONE), callCaptor.capture(), factsCaptor.capture());

        RememberedCall remembered = callCaptor.getValue();
        assertThat(remembered.scenarioKey()).isEqualTo("support-scenario");
        assertThat(remembered.disposition()).isEqualTo(Disposition.SUCCESSFUL);
        assertThat(remembered.summary()).isEqualTo("Mijoz buyurtmasi muvaffaqiyatli yetkazildi.");

        Map<String, Object> facts = factsCaptor.getValue();
        assertThat(facts).containsEntry("orderId", "ORD-12345");
    }

    @Test
    void answerChat_whenSessionEndsWithoutPhone_doesNotRememberCall() {
        when(repository.findMessages(1L)).thenReturn(List.of());
        when(textTurnEngine.reply(any(DialogSession.class), anyString())).thenAnswer(inv -> {
            DialogSession ds = inv.getArgument(0);
            ds.setDisposition(Disposition.HUNG_UP);
            ds.setEnded(true);
            return new TextTurn("Xayr!", List.of());
        });

        ChatSession session = new ChatSession(1L, "key-6", COMPANY_ID, AGENT_ID, 100L, null, null,
                "uz", "ORDER", Map.of(), null, false, 2, NOW, NOW);

        ChatTurn turn = conversations.answer(session, "Xayr!");

        assertThat(turn.session().ended()).isTrue();
        verify(clientMemoryUseCase, never()).rememberCall(anyLong(), anyString(), any(), any());
    }
}
