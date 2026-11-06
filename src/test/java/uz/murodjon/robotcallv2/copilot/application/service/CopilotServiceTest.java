package uz.murodjon.robotcallv2.copilot.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.copilot.application.dto.AskCopilotRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.CopilotReplyResponse;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotModelPort;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotRepository;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotToolInvoker;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotModelReply;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotProposalStatus;
import uz.murodjon.robotcallv2.copilot.infrastructure.config.CopilotProperties;
import uz.murodjon.robotcallv2.mcp.application.port.input.McpServerUseCase;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CopilotServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long USER_ID = 9L;
    private static final long SESSION_ID = 5L;

    private CopilotRepository repository;
    private CopilotModelPort modelPort;
    private McpServerUseCase mcpServerUseCase;
    private CallBillingUseCase callBillingUseCase;
    private CurrentUser currentUser;
    private CopilotService service;

    @BeforeEach
    void setUp() {
        repository = mock(CopilotRepository.class);
        modelPort = mock(CopilotModelPort.class);
        mcpServerUseCase = mock(McpServerUseCase.class);
        callBillingUseCase = mock(CallBillingUseCase.class);
        currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(Optional.of(USER_ID));
        when(currentUser.wrap(any())).thenAnswer(call -> call.getArgument(0));
        when(callBillingUseCase.hasBalanceForCall(COMPANY_ID)).thenReturn(true);
        when(repository.createSession(eq(COMPANY_ID), any())).thenAnswer(call -> {
            CopilotSession started = call.getArgument(1);
            return new CopilotSession(SESSION_ID, COMPANY_ID, started.userId(), started.title(), null, null);
        });
        AtomicLong ids = new AtomicLong(100);
        when(repository.createMessage(eq(COMPANY_ID), any())).thenAnswer(call -> {
            CopilotMessage m = call.getArgument(1);
            return new CopilotMessage(ids.incrementAndGet(), m.sessionId(), m.role(), m.content(), m.toolName(),
                    m.toolArguments(), m.toolError(), m.promptTokens(), m.completionTokens(), 0, null, m.proposalStatus());
        });
        service = new CopilotService(repository, modelPort, mcpServerUseCase, callBillingUseCase,
                mock(CompanyConfigUseCase.class), currentUser, new CopilotProperties(null, 0, 2, 0),
                Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC));
    }

    /** The model calls the given tools, in order, and then answers. */
    private void givenModelCalls(String... tools) {
        when(modelPort.generateReply(anyString(), anyList(), anyList(), any(), any())).thenAnswer(call -> {
            CopilotToolInvoker invoker = call.getArgument(4);
            for (String tool : tools) {
                invoker.callTool(tool, "{}");
            }
            return new CopilotModelReply("javob", 1200, 300);
        });
    }

    @Test
    void aQuestionStartsAConversationRunsToolsAsDraftOnlyAndChargesTheReply() {
        givenModelCalls("list_calls");
        when(mcpServerUseCase.callServerToolAsDraftOnly(COMPANY_ID, "list_calls", "{}")).thenReturn("[]");
        when(callBillingUseCase.chargeCopilotReply(eq(COMPANY_ID), anyLong(), any())).thenReturn(45L);

        CopilotReplyResponse response = service.ask(COMPANY_ID, new AskCopilotRequest(null, "Nega transferlar ko'paydi?", null, null, null));

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.reply().content()).isEqualTo("javob");
        assertThat(response.reply().chargedUzs()).isEqualTo(45);
        assertThat(response.toolCalls()).singleElement()
                .satisfies(tool -> {
                    assertThat(tool.role()).isEqualTo(CopilotMessageRole.TOOL);
                    assertThat(tool.toolName()).isEqualTo("list_calls");
                    assertThat(tool.content()).isEqualTo("[]");
                    assertThat(tool.toolError()).isFalse();
                });
        ArgumentCaptor<CallUsage> usage = ArgumentCaptor.forClass(CallUsage.class);
        verify(callBillingUseCase).chargeCopilotReply(eq(COMPANY_ID), eq(response.reply().id()), usage.capture());
        assertThat(usage.getValue().promptTokens()).isEqualTo(1200);
        assertThat(usage.getValue().completionTokens()).isEqualTo(300);
        verify(repository).updateMessageCharge(response.reply().id(), 45L);
        verify(repository).touchSession(SESSION_ID);
    }

    @Test
    void aQuestionStreamEmitsSessionToolAndDoneEvents() throws Exception {
        givenModelCalls("list_calls");
        when(mcpServerUseCase.callServerToolAsDraftOnly(COMPANY_ID, "list_calls", "{}")).thenReturn("[]");
        when(callBillingUseCase.chargeCopilotReply(eq(COMPANY_ID), anyLong(), any())).thenReturn(45L);
        EventSink sink = mock(EventSink.class);

        EventSubscription sub = service.askStream(COMPANY_ID, new AskCopilotRequest(null, "Stream savol", null, null, null), sink);
        assertThat(sub).isNotNull();

        // Give virtual thread a moment to run
        Thread.sleep(150);

        verify(sink).send(eq("session"), any());
        verify(sink).send(eq("tool_call"), any());
        verify(sink).send(eq("tool_result"), any());
        verify(sink, atLeastOnce()).send(eq("done"), any());
    }

    @Test
    void toolCallsStopPastTheBudget() {
        givenModelCalls("a", "b", "c");
        when(mcpServerUseCase.callServerToolAsDraftOnly(eq(COMPANY_ID), anyString(), anyString())).thenReturn("ok");

        CopilotReplyResponse response = service.ask(COMPANY_ID, new AskCopilotRequest(null, "o'zgartir", null, null, null));

        assertThat(response.toolCalls()).hasSize(2);
        verify(mcpServerUseCase, times(2)).callServerToolAsDraftOnly(eq(COMPANY_ID), anyString(), anyString());
    }

    @Test
    void aToolThatWritesIsKeptAsAProposalUnlessChangesAreAppliedAtOnce() {
        when(mcpServerUseCase.findServerTools()).thenReturn(List.of(
                new McpTool("get_agent", "", "{}", true, false),
                new McpTool("update_agent_draft", "", "{}", false, false)));
        givenModelCalls("get_agent", "update_agent_draft");
        when(mcpServerUseCase.callServerToolAsDraftOnly(eq(COMPANY_ID), anyString(), anyString())).thenReturn("ok");

        CopilotReplyResponse response = service.ask(COMPANY_ID, new AskCopilotRequest(null, "o'zgartir", null, null, null));

        verify(mcpServerUseCase).callServerToolAsDraftOnly(COMPANY_ID, "get_agent", "{}");
        verify(mcpServerUseCase, never()).callServerToolAsDraftOnly(COMPANY_ID, "update_agent_draft", "{}");
        assertThat(response.toolCalls().get(1).proposalStatus()).isEqualTo(CopilotProposalStatus.PENDING);
        assertThat(response.toolCalls().get(1).content()).startsWith("PROPOSED");

        service.ask(COMPANY_ID, new AskCopilotRequest(null, "o'zgartir", null, true, null));
        verify(mcpServerUseCase).callServerToolAsDraftOnly(COMPANY_ID, "update_agent_draft", "{}");
    }

    @Test
    void applyingAProposalRunsTheToolOnceAndTellsTheModelNextTime() {
        CopilotMessage pending = new CopilotMessage(55L, SESSION_ID, CopilotMessageRole.TOOL, "", "update_agent_draft",
                "{\"agentId\":4}", false, 0, 0, 0, null, CopilotProposalStatus.PENDING);
        when(repository.findMessageById(55L)).thenReturn(pending);
        when(repository.findSessionByCompanyIdAndUserIdAndId(COMPANY_ID, USER_ID, SESSION_ID))
                .thenReturn(new CopilotSession(SESSION_ID, COMPANY_ID, USER_ID, "t", null, null));
        when(repository.updateProposal(eq(55L), any(), anyString(), anyBoolean())).thenReturn(true);
        when(mcpServerUseCase.callServerToolAsDraftOnly(COMPANY_ID, "update_agent_draft", "{\"agentId\":4}")).thenReturn("saved");

        service.applyProposal(COMPANY_ID, 55L);

        verify(repository).updateProposal(55L, CopilotProposalStatus.APPLIED, "saved", false);
        ArgumentCaptor<CopilotMessage> note = ArgumentCaptor.forClass(CopilotMessage.class);
        verify(repository).createMessage(eq(COMPANY_ID), note.capture());
        assertThat(note.getValue().role()).isEqualTo(CopilotMessageRole.NOTE);
        assertThat(note.getValue().content()).contains("#55").contains("saved");
    }

    @Test
    void aSettledProposalCannotBeAppliedAgain() {
        when(repository.findMessageById(56L)).thenReturn(new CopilotMessage(56L, SESSION_ID, CopilotMessageRole.TOOL,
                "ok", "update_agent_draft", "{}", false, 0, 0, 0, null, CopilotProposalStatus.APPLIED));
        when(repository.findSessionByCompanyIdAndUserIdAndId(COMPANY_ID, USER_ID, SESSION_ID))
                .thenReturn(new CopilotSession(SESSION_ID, COMPANY_ID, USER_ID, "t", null, null));

        assertThatThrownBy(() -> service.applyProposal(COMPANY_ID, 56L)).isInstanceOf(ConflictException.class);
        verify(mcpServerUseCase, never()).callServerToolAsDraftOnly(anyLong(), anyString(), anyString());
    }

    @Test
    void anApiKeyCannotAskTheCopilot() {
        when(currentUser.id()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(COMPANY_ID, new AskCopilotRequest(null, "?", null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(modelPort);
    }

    @Test
    void anotherUsersSessionCannotBeReused() {
        when(repository.findSessionByCompanyIdAndUserIdAndId(COMPANY_ID, USER_ID, 77L)).thenReturn(null);

        assertThatThrownBy(() -> service.ask(COMPANY_ID, new AskCopilotRequest(77L, "?", null, null, null)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(modelPort);
    }

    @Test
    void withoutBalanceNothingIsAsked() {
        when(callBillingUseCase.hasBalanceForCall(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.ask(COMPANY_ID, new AskCopilotRequest(null, "?", null, null, null)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(modelPort);
        verify(repository, never()).createSession(anyLong(), any());
    }

    @Test
    void earlierToolRowsAreNotSentBackToTheModel() {
        when(repository.findSessionByCompanyIdAndUserIdAndId(COMPANY_ID, USER_ID, SESSION_ID))
                .thenReturn(new CopilotSession(SESSION_ID, COMPANY_ID, USER_ID, "t", null, null));
        when(repository.findMessagesBySessionId(SESSION_ID)).thenReturn(List.of(
                CopilotMessage.user(SESSION_ID, "birinchi"),
                CopilotMessage.tool(SESSION_ID, "list_calls", "{}", "[]", false),
                CopilotMessage.assistant(SESSION_ID, new CopilotModelReply("javob", 1, 1))));
        givenModelCalls();

        service.ask(COMPANY_ID, new AskCopilotRequest(SESSION_ID, "ikkinchi", null, null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CopilotMessage>> history = ArgumentCaptor.forClass(List.class);
        verify(modelPort).generateReply(anyString(), history.capture(), anyList(), any(), any());
        assertThat(history.getValue()).extracting(CopilotMessage::content)
                .containsExactly("birinchi", "javob", "ikkinchi");
    }
}
