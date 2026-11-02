package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionExtract;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionVariable;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVariableType;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** An agent's EXTRACT_VARIABLE function records its values on the call, and the remembered ones in the client's memory. */
class AgentFunctionToolsTest {

    private static final long COMPANY = 1L;

    private DialogSession session;
    private ClientMemoryUseCase memory;

    @BeforeEach
    void setUp() {
        session = DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(Map.of(), "goal").withPhone("+998901234567"))
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(42L)
                .companyId(COMPANY)
                .companyName("Uysot")
                .build();
        memory = mock(ClientMemoryUseCase.class);
    }

    private static AiAgent agentWith(AgentFunctionVariable... variables) {
        return AiAgent.builder().name("Agent").functions(List.of(AgentFunction.builder().id("a")
                .type(AgentFunctionType.EXTRACT_VARIABLE).name("rememberFlatInterest")
                .extract(new AgentFunctionExtract(List.of(variables))).build())).build();
    }

    private ToolCallback toolOf(AiAgent agent) {
        List<ToolCallback> tools = AgentFunctionTools.buildExtractionTools(agent, session, COMPANY, memory);
        assertThat(tools).hasSize(1);
        return tools.getFirst();
    }

    @Test
    void aRememberedVariableGoesToTheClientsMemoryAndTheOthersStayOnTheCall() {
        ToolCallback tool = toolOf(agentWith(
                new AgentFunctionVariable("interestedFlat", AgentVariableType.STRING, "the flat", List.of(), true),
                new AgentFunctionVariable("visitDay", AgentVariableType.STRING, "when", List.of(), false)));

        tool.call("{\"interestedFlat\": \"Yunusobod A blok №45\", \"visitDay\": \"shanba\"}");

        assertThat(session.outcome()).containsEntry("interestedFlat", "Yunusobod A blok №45")
                .containsEntry("visitDay", "shanba");
        verify(memory).rememberFacts(COMPANY, "+998901234567", Map.of("interestedFlat", "Yunusobod A blok №45"));
    }

    @Test
    void nothingIsRememberedWhenNoVariableAsksForIt() {
        ToolCallback tool = toolOf(agentWith(
                new AgentFunctionVariable("visitDay", AgentVariableType.STRING, "when", List.of(), false)));

        tool.call("{\"visitDay\": \"shanba\"}");

        verify(memory, never()).rememberFacts(anyLong(), anyString(), anyMap());
    }

    @Test
    void aRememberedVariableTheModelLeftOutIsNotWritten() {
        ToolCallback tool = toolOf(agentWith(
                new AgentFunctionVariable("interestedFlat", AgentVariableType.STRING, "the flat", List.of(), true),
                new AgentFunctionVariable("visitDay", AgentVariableType.STRING, "when", List.of(), false)));

        tool.call("{\"visitDay\": \"shanba\"}");

        verify(memory, never()).rememberFacts(anyLong(), anyString(), anyMap());
    }

    @Test
    void aFailingMemoryDoesNotFailTheCall() {
        ToolCallback tool = toolOf(agentWith(
                new AgentFunctionVariable("interestedFlat", AgentVariableType.STRING, "the flat", List.of(), true)));
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(memory)
                .rememberFacts(anyLong(), anyString(), anyMap());

        assertThat(tool.call("{\"interestedFlat\": \"A-45\"}")).contains("Yozib olindi");
        assertThat(session.outcome()).containsEntry("interestedFlat", "A-45");
    }
}
