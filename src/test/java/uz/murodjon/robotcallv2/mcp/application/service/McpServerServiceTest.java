package uz.murodjon.robotcallv2.mcp.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentEvaluationUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.pack.application.port.input.PackUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.security.CurrentUser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerServiceTest {

    private static final long COMPANY_ID = 1L;

    private final ObjectMapper json = new ObjectMapper();
    private final AiAgentUseCase aiAgentUseCase = mock(AiAgentUseCase.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final McpServerService service = new McpServerService(new McpServerToolCatalog(aiAgentUseCase,
            mock(AgentEvaluationUseCase.class), mock(PackUseCase.class), mock(ReportUseCase.class), currentUser,
            Validation.buildDefaultValidatorFactory().getValidator(), mock(PlatformTransactionManager.class)));

    @Test
    void initializeAgreesOnASupportedProtocolAndOffersTools() throws Exception {
        JsonNode answer = send("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26"}}""");

        assertThat(answer.at("/result/protocolVersion").asText()).isEqualTo("2025-03-26");
        assertThat(answer.at("/result/capabilities/tools").isObject()).isTrue();
    }

    @Test
    void aNotificationGetsNoAnswer() {
        assertThat(service.handleMessage(COMPANY_ID, """
                {"jsonrpc":"2.0","method":"notifications/initialized"}""")).isEmpty();
    }

    @Test
    void malformedJsonUnknownMethodsAndUnknownToolsAreJsonRpcErrors() throws Exception {
        assertThat(send("{not json").at("/error/code").asInt()).isEqualTo(-32700);
        assertThat(send("""
                {"jsonrpc":"2.0","id":2,"method":"resources/list"}""").at("/error/code").asInt()).isEqualTo(-32601);
        assertThat(send("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"drop_database"}}""")
                .at("/error/code").asInt()).isEqualTo(-32602);
    }

    @Test
    void aCallerSeesOnlyTheToolsItsPermissionsOpen() throws Exception {
        holding(Permission.AI_AGENT_READ);

        List<String> names = new ArrayList<>();
        send("""
                {"jsonrpc":"2.0","id":4,"method":"tools/list"}""").at("/result/tools")
                .forEach(tool -> names.add(tool.get("name").asText()));

        assertThat(names).contains("list_agents", "get_agent", "export_agent")
                .doesNotContain("create_agent", "run_evaluation", "list_calls");
    }

    @Test
    void aToolWithoutPermissionAnswersWithAnErrorResultTheModelCanRead() throws Exception {
        holding(Permission.AI_AGENT_READ);

        JsonNode answer = callTool("create_agent", """
                {"name":"Sotuv"}""");

        assertThat(answer.at("/result/isError").asBoolean()).isTrue();
        assertThat(answer.at("/result/content/0/text").asText()).startsWith("MCP_TOOL_FORBIDDEN");
        verify(aiAgentUseCase, never()).createAgent(anyLong(), any());
    }

    @Test
    void aDraftsOnlyCallerCreatesItsAgentSwitchedOff() throws Exception {
        holding(Permission.AGENT_DRAFT_EDIT);

        callTool("create_agent", """
                {"name":"Sotuv","systemPrompt":"Siz sotuv operatorisiz","enabled":true}""");

        ArgumentCaptor<CreateAiAgentRequest> request = ArgumentCaptor.forClass(CreateAiAgentRequest.class);
        verify(aiAgentUseCase).createAgent(eq(COMPANY_ID), request.capture());
        assertThat(request.getValue().enabled()).isFalse();
    }

    @Test
    void aDraftsOnlyCallerMayNotEditAnAgentWhoseCallsRunOnItsDraft() throws Exception {
        holding(Permission.AGENT_DRAFT_EDIT);
        AiAgentRow liveOnDraft = mock(AiAgentRow.class);
        when(liveOnDraft.enabled()).thenReturn(true);
        when(liveOnDraft.publishedVersion()).thenReturn(null);
        when(aiAgentUseCase.findAgentRow(COMPANY_ID, 7L)).thenReturn(liveOnDraft);

        JsonNode answer = callTool("update_agent_draft", """
                {"agentId":7,"systemPrompt":"yangi"}""");

        assertThat(answer.at("/result/content/0/text").asText()).startsWith("MCP_AGENT_LIVE_ON_DRAFT");
        verify(aiAgentUseCase, never()).updateAgent(anyLong(), anyLong(), any());
    }

    @Test
    void aMisspeltArgumentIsRefusedRatherThanDropped() throws Exception {
        holding(Permission.AI_AGENT_EDIT);

        JsonNode answer = callTool("create_agent", """
                {"name":"Sotuv","systemPromt":"..."}""");

        assertThat(answer.at("/result/content/0/text").asText()).startsWith("MCP_TOOL_ARGUMENTS_INVALID");
    }

    private void holding(Permission... permissions) {
        Set<Permission> held = EnumSet.noneOf(Permission.class);
        held.addAll(List.of(permissions));
        when(currentUser.hasPermission(any())).thenAnswer(invocation -> held.contains(invocation.<Permission>getArgument(0)));
    }

    private JsonNode callTool(String name, String arguments) throws Exception {
        return send("""
                {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
                .formatted(name, arguments));
    }

    private JsonNode send(String body) throws Exception {
        Optional<String> answer = service.handleMessage(COMPANY_ID, body);
        assertThat(answer).isPresent();
        return json.readTree(answer.get());
    }
}
