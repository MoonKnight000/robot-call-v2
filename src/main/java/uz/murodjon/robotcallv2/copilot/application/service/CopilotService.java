package uz.murodjon.robotcallv2.copilot.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.copilot.application.dto.AskCopilotRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.CopilotReplyResponse;
import uz.murodjon.robotcallv2.copilot.application.dto.RewritePromptRequest;
import uz.murodjon.robotcallv2.copilot.application.dto.RewritePromptResponse;
import uz.murodjon.robotcallv2.copilot.application.port.input.CopilotUseCase;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotModelPort;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotRepository;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotToolInvoker;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotModelReply;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotProposalStatus;
import uz.murodjon.robotcallv2.copilot.domain.service.CopilotInstructions;
import uz.murodjon.robotcallv2.copilot.domain.service.CopilotToolResultMinifier;
import uz.murodjon.robotcallv2.copilot.domain.service.CopilotToolSelector;
import uz.murodjon.robotcallv2.copilot.infrastructure.config.CopilotProperties;
import uz.murodjon.robotcallv2.mcp.application.port.input.McpServerUseCase;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpTool;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.shared.stream.EventSink;
import uz.murodjon.robotcallv2.shared.stream.EventSubscription;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asks the model one question at a time, with the platform's MCP tools in reach — always in
 * drafts-only mode, whoever asks — and keeps every question, answer and tool call. The reply's
 * tokens are charged to the company like any other AI work.
 */
@Service
public class CopilotService implements CopilotUseCase {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    /** A tool answer longer than this is cut before the model reads it, and before it is stored. */
    static final int MAX_TOOL_ANSWER_CHARS = CopilotToolResultMinifier.MAX_MINIFIED_CHARS;

    private final CopilotRepository repository;
    private final CopilotModelPort modelPort;
    private final McpServerUseCase mcpServerUseCase;
    private final CallBillingUseCase callBillingUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final CurrentUser currentUser;
    private final CopilotProperties copilotProperties;
    private final Clock clock;

    public CopilotService(CopilotRepository repository, CopilotModelPort modelPort,
                          McpServerUseCase mcpServerUseCase, CallBillingUseCase callBillingUseCase,
                          CompanyConfigUseCase companyConfigUseCase, CurrentUser currentUser,
                          CopilotProperties copilotProperties, Clock clock) {
        this.repository = repository;
        this.modelPort = modelPort;
        this.mcpServerUseCase = mcpServerUseCase;
        this.callBillingUseCase = callBillingUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.currentUser = currentUser;
        this.copilotProperties = copilotProperties;
        this.clock = clock;
    }

    @Override
    public CopilotReplyResponse ask(long companyId, AskCopilotRequest request) {
        long userId = requireUserId();
        if (!callBillingUseCase.hasBalanceForCall(companyId)) {
            throw new ConflictException(ErrorCode.COPILOT_NO_BALANCE);
        }
        CopilotSession session = request.sessionId() == null
                ? repository.createSession(companyId, CopilotSession.started(userId, request.message()))
                : requireSession(companyId, userId, request.sessionId());

        List<CopilotMessage> history = new ArrayList<>(findRecentConversation(session.id()));
        CopilotMessage question = repository.createMessage(companyId, CopilotMessage.user(session.id(), request.message()));
        history.add(question);

        List<McpTool> availableTools = mcpServerUseCase.findServerTools();
        List<McpTool> tools = CopilotToolSelector.selectTools(request.context(), request.message(), availableTools);
        RecordingToolInvoker invoker = new RecordingToolInvoker(companyId, session.id(),
                ToolRun.of(tools, request.applyChangesOrDefault()));
        CopilotModelReply reply = modelPort.generateReply(
                CopilotInstructions.forQuestion(today(companyId), request.context()), history, tools, invoker);

        CopilotMessage answer = repository.createMessage(companyId, CopilotMessage.assistant(session.id(), reply));
        long chargedUzs = callBillingUseCase.chargeCopilotReply(companyId, answer.id(),
                new CallUsage(0, reply.promptTokens(), reply.completionTokens(), 0, 0));
        repository.updateMessageCharge(answer.id(), chargedUzs);
        repository.touchSession(session.id());
        return new CopilotReplyResponse(session.id(), answer.withCharge(chargedUzs), invoker.calls);
    }

    @Override
    public EventSubscription askStream(long companyId, AskCopilotRequest request, EventSink sink) {
        long userId = requireUserId();
        if (!callBillingUseCase.hasBalanceForCall(companyId)) {
            throw new ConflictException(ErrorCode.COPILOT_NO_BALANCE);
        }
        CopilotSession session = request.sessionId() == null
                ? repository.createSession(companyId, CopilotSession.started(userId, request.message()))
                : requireSession(companyId, userId, request.sessionId());

        List<CopilotMessage> history = new ArrayList<>(findRecentConversation(session.id()));
        CopilotMessage question = repository.createMessage(companyId, CopilotMessage.user(session.id(), request.message()));
        history.add(question);

        try {
            sink.send("session", session);
            sink.send("status", Map.of("step", "THINKING", "message", "Savolingiz o'rganilmoqda..."));
        } catch (IOException e) {
            log.warn("Failed to send initial session event for copilot session {}", session.id(), e);
        }

        List<McpTool> availableTools = mcpServerUseCase.findServerTools();
        List<McpTool> tools = CopilotToolSelector.selectTools(request.context(), request.message(), availableTools);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        Thread task = Thread.ofVirtual().name("copilot-ask-" + session.id())
                .start(currentUser.wrap(() -> {
            try {
                StreamingToolInvoker invoker = new StreamingToolInvoker(companyId, session.id(), sink, cancelled,
                        ToolRun.of(tools, request.applyChangesOrDefault()));
                CopilotModelReply reply = modelPort.generateReply(
                        CopilotInstructions.forQuestion(today(companyId), request.context()), history, tools, invoker);

                if (cancelled.get()) {
                    return;
                }

                CopilotMessage answer = repository.createMessage(companyId, CopilotMessage.assistant(session.id(), reply));
                long chargedUzs = callBillingUseCase.chargeCopilotReply(companyId, answer.id(),
                        new CallUsage(0, reply.promptTokens(), reply.completionTokens(), 0, 0));
                repository.updateMessageCharge(answer.id(), chargedUzs);
                repository.touchSession(session.id());

                CopilotMessage finalAnswer = answer.withCharge(chargedUzs);

                try {
                    sink.send("status", Map.of("step", "GENERATING", "message", "Javob shakllantirilmoqda..."));
                } catch (IOException ignored) {
                }
                streamTextDeltas(reply.text(), sink, cancelled);

                if (!cancelled.get()) {
                    sink.send("done", new CopilotReplyResponse(session.id(), finalAnswer, invoker.calls));
                }
            } catch (Exception e) {
                log.warn("Copilot stream failed for company {}", companyId, e);
                try {
                    sink.send("error", Map.of("message", e.getMessage() != null ? e.getMessage() : "Error"));
                } catch (IOException ignored) {
                }
            }
        }));

        return () -> {
            cancelled.set(true);
            task.interrupt();
        };
    }

    @Override
    public PageableData<CopilotSession> filterSessions(long companyId, CopilotSessionFilter filter) {
        return repository.findSessionPage(companyId, requireUserId(), filter);
    }

    @Override
    public List<CopilotMessage> findMessages(long companyId, long sessionId) {
        CopilotSession session = requireSession(companyId, requireUserId(), sessionId);
        return repository.findMessagesBySessionId(session.id());
    }

    @Override
    public void deleteSession(long companyId, long sessionId) {
        long userId = requireUserId();
        requireSession(companyId, userId, sessionId);
        repository.deleteSession(companyId, userId, sessionId);
    }

    @Override
    public CopilotMessage applyProposal(long companyId, long messageId) {
        CopilotMessage proposal = requirePendingProposal(companyId, messageId);
        String answer;
        boolean error = false;
        try {
            answer = mcpServerUseCase.callServerToolAsDraftOnly(companyId, proposal.toolName(), proposal.toolArguments());
        } catch (AppException e) {
            answer = e.code().name() + ": " + e.getMessage();
            error = true;
        }
        CopilotProposalStatus status = error ? CopilotProposalStatus.FAILED : CopilotProposalStatus.APPLIED;
        settle(proposal, status, cut(answer), error);
        repository.createMessage(companyId, CopilotMessage.note(proposal.sessionId(), ("[Panel] The user applied "
                + "change #%d (%s). %s: %s").formatted(proposal.id(), proposal.toolName(),
                error ? "It FAILED" : "Result", abbreviate(answer))));
        repository.touchSession(proposal.sessionId());
        return repository.findMessageById(proposal.id());
    }

    @Override
    public CopilotMessage rejectProposal(long companyId, long messageId) {
        CopilotMessage proposal = requirePendingProposal(companyId, messageId);
        settle(proposal, CopilotProposalStatus.REJECTED, "", false);
        repository.createMessage(companyId, CopilotMessage.note(proposal.sessionId(),
                "[Panel] The user REJECTED change #%d (%s); it was not applied.".formatted(proposal.id(), proposal.toolName())));
        return repository.findMessageById(proposal.id());
    }

    @Override
    public RewritePromptResponse rewritePrompt(long companyId, RewritePromptRequest request) {
        String prompt = request.prompt() == null ? "" : request.prompt().strip();
        String instruction = request.instruction() == null ? "" : request.instruction().strip();
        if (prompt.isEmpty() && instruction.isEmpty()) {
            throw new ValidationException(ErrorCode.PROMPT_REWRITE_EMPTY);
        }
        if (!callBillingUseCase.hasBalanceForCall(companyId)) {
            throw new ConflictException(ErrorCode.COPILOT_NO_BALANCE);
        }
        String message = "CURRENT PROMPT:\n" + prompt + "\n\nINSTRUCTION:\n" + instruction;
        CopilotModelReply reply = modelPort.generateText(CopilotInstructions.forPromptRewrite(), message);
        if (reply.text() != null && reply.text().strip().equals(CopilotInstructions.OUT_OF_SCOPE)) {
            throw new ValidationException(ErrorCode.PROMPT_REWRITE_OUT_OF_SCOPE);
        }
        return new RewritePromptResponse(reply.text());
    }

    /** An API key has no user to own a conversation; the copilot is the panel's. */
    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }

    private CopilotSession requireSession(long companyId, long userId, long sessionId) {
        CopilotSession session = repository.findSessionByCompanyIdAndUserIdAndId(companyId, userId, sessionId);
        if (session == null) {
            throw new NotFoundException(ErrorCode.COPILOT_SESSION_NOT_FOUND, sessionId);
        }
        return session;
    }

    /** A proposal in one of this user's own conversations, still waiting. */
    private CopilotMessage requirePendingProposal(long companyId, long messageId) {
        CopilotMessage proposal = repository.findMessageById(messageId);
        if (proposal == null || proposal.proposalStatus() == null) {
            throw new NotFoundException(ErrorCode.COPILOT_PROPOSAL_NOT_FOUND, messageId);
        }
        requireSession(companyId, requireUserId(), proposal.sessionId());
        if (!proposal.isPendingProposal()) {
            throw new ConflictException(ErrorCode.COPILOT_PROPOSAL_SETTLED, messageId);
        }
        return proposal;
    }

    private void settle(CopilotMessage proposal, CopilotProposalStatus status, String content, boolean error) {
        if (!repository.updateProposal(proposal.id(), status, content, error)) {
            throw new ConflictException(ErrorCode.COPILOT_PROPOSAL_SETTLED, proposal.id());
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    /** The last questions and answers; tool rows stay out — the answers already say what they found. */
    private List<CopilotMessage> findRecentConversation(long sessionId) {
        List<CopilotMessage> said = repository.findMessagesBySessionId(sessionId).stream()
                .filter(message -> message.role() != CopilotMessageRole.TOOL)
                .toList();
        int keep = copilotProperties.maxHistory();
        List<CopilotMessage> trimmed = said.size() > keep ? said.subList(said.size() - keep, said.size()) : said;
        return pruneOlderMessages(trimmed);
    }

    /**
     * Prevents long conversations from bloating the context window with older multi-paragraph replies.
     * Keeps the most recent 2 turns (4 messages) verbatim, while capping earlier assistant answers.
     */
    private static List<CopilotMessage> pruneOlderMessages(List<CopilotMessage> messages) {
        if (messages.size() <= 4) {
            return messages;
        }
        List<CopilotMessage> result = new ArrayList<>(messages.size());
        int recentThreshold = messages.size() - 4;
        for (int i = 0; i < messages.size(); i++) {
            CopilotMessage m = messages.get(i);
            if (i < recentThreshold && m.role() == CopilotMessageRole.ASSISTANT && m.content() != null && m.content().length() > 800) {
                String compact = m.content().substring(0, 800) + "… [oldingi javob qisqartirildi]";
                result.add(new CopilotMessage(m.id(), m.sessionId(), m.role(), compact, m.toolName(),
                        m.toolArguments(), m.toolError(), m.promptTokens(), m.completionTokens(), m.chargedUzs(),
                        m.createdAt(), m.proposalStatus()));
            } else {
                result.add(m);
            }
        }
        return result;
    }

    private LocalDate today(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return LocalDate.now(config != null ? clock.withZone(config.zoneId()) : clock);
    }

    private void streamTextDeltas(String text, EventSink sink, AtomicBoolean cancelled) {
        if (text == null || text.isBlank() || cancelled.get()) {
            return;
        }
        String[] tokens = text.split("(?<=\\s+)");
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < tokens.length && !cancelled.get(); i++) {
            chunk.append(tokens[i]);
            if ((i + 1) % 3 == 0 || i == tokens.length - 1) {
                try {
                    sink.send("delta", Map.of("text", chunk.toString()));
                    chunk.setLength(0);
                    Thread.sleep(15);
                } catch (IOException | InterruptedException e) {
                    break;
                }
            }
        }
    }

    /**
     * Runs one tool call of the model in drafts-only mode — or, for a tool that writes when the
     * user did not ask for changes to be applied at once, keeps it as a PENDING proposal and tells
     * the model so — and stores it, so a reply that fails half-way still leaves its trail.
     */
    private CopilotMessage runTool(long companyId, long sessionId, ToolRun run, String name, String argumentsJson) {
        if (!run.applyChanges() && run.writeTools().contains(name)) {
            CopilotMessage proposal = repository.createMessage(companyId,
                    CopilotMessage.proposal(sessionId, name, argumentsJson));
            return proposal.withProposalOutcome(CopilotProposalStatus.PENDING, ("PROPOSED: change #%d (%s) is "
                    + "waiting for the user's approval in the panel and has NOT been applied. Tell the user what "
                    + "it changes (before -> after) and ask them to press \"Qo'llash\". Do not call tools that "
                    + "need its result until they say it is applied.").formatted(proposal.id(), name), false);
        }
        String answer;
        boolean error = false;
        try {
            answer = mcpServerUseCase.callServerToolAsDraftOnly(companyId, name, argumentsJson);
            answer = CopilotToolResultMinifier.minify(name, answer);
        } catch (AppException e) {
            answer = e.code().name() + ": " + e.getMessage();
            error = true;
        } catch (RuntimeException e) {
            log.warn("Copilot tool {} failed for company {}", name, companyId, e);
            answer = ErrorCode.INTERNAL_SERVER_ERROR.name() + ": the tool failed";
            error = true;
        }
        return repository.createMessage(companyId, CopilotMessage.tool(sessionId, name, argumentsJson, cut(answer), error));
    }

    private static String cut(String answer) {
        return answer.length() > MAX_TOOL_ANSWER_CHARS
                ? answer.substring(0, MAX_TOOL_ANSWER_CHARS) + "\n…[cut: the answer was longer — ask for a smaller page or one item]"
                : answer;
    }

    /**
     * How tool calls run within one reply.
     *
     * @param writeTools   the tools that change something — the rest only read
     * @param applyChanges the user asked for changes to be applied without asking again
     */
    private record ToolRun(Set<String> writeTools, boolean applyChanges) {

        static ToolRun of(List<McpTool> tools, boolean applyChanges) {
            return new ToolRun(tools.stream().filter(tool -> !tool.readOnly()).map(McpTool::name)
                    .collect(Collectors.toSet()), applyChanges);
        }
    }

    /** Runs the model's tool calls and stops the model past the call budget. */
    private final class RecordingToolInvoker implements CopilotToolInvoker {

        private final long companyId;
        private final long sessionId;
        private final ToolRun run;
        private final List<CopilotMessage> calls = new ArrayList<>();

        private RecordingToolInvoker(long companyId, long sessionId, ToolRun run) {
            this.companyId = companyId;
            this.sessionId = sessionId;
            this.run = run;
        }

        @Override
        public String callTool(String name, String argumentsJson) {
            if (calls.size() >= copilotProperties.maxToolCalls()) {
                return "TOOL_BUDGET_EXHAUSTED: answer the user now with what you already have.";
            }
            CopilotMessage call = runTool(companyId, sessionId, run, name, argumentsJson);
            calls.add(call);
            return call.content();
        }
    }

    /** The same, telling the EventSink about every tool as it starts and as it answers. */
    private final class StreamingToolInvoker implements CopilotToolInvoker {

        private final long companyId;
        private final long sessionId;
        private final EventSink sink;
        private final AtomicBoolean cancelled;
        private final ToolRun run;
        private final List<CopilotMessage> calls = new ArrayList<>();

        private StreamingToolInvoker(long companyId, long sessionId, EventSink sink, AtomicBoolean cancelled,
                                     ToolRun run) {
            this.companyId = companyId;
            this.sessionId = sessionId;
            this.sink = sink;
            this.cancelled = cancelled;
            this.run = run;
        }

        @Override
        public String callTool(String name, String argumentsJson) {
            if (cancelled.get()) {
                return "CANCELLED";
            }
            if (calls.size() >= copilotProperties.maxToolCalls()) {
                return "TOOL_BUDGET_EXHAUSTED: answer the user now with what you already have.";
            }
            String toolTitle = resolveToolHumanTitle(name);
            try {
                sink.send("status", Map.of("step", "TOOL_RUNNING", "toolName", name, "message", toolTitle + "..."));
                sink.send("tool_call", Map.of("toolName", name, "arguments", argumentsJson));
            } catch (IOException ignored) {
            }
            CopilotMessage call = runTool(companyId, sessionId, run, name, argumentsJson);
            calls.add(call);
            try {
                sink.send("status", Map.of("step", "TOOL_DONE", "toolName", name, "message", toolTitle + " yakunlandi"));
                sink.send(call.isPendingProposal() ? "proposal" : "tool_result", call);
            } catch (IOException ignored) {
            }
            return call.content();
        }
    }

    private static String resolveToolHumanTitle(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Ma'lumotlar tahlil qilinmoqda";
        }
        return switch (toolName) {
            case "list_ai_agents" -> "AI agentlar ro'yxati olinmoqda";
            case "get_ai_agent" -> "AI agent ma'lumotlari yuklanmoqda";
            case "update_ai_agent", "update_ai_agent_prompt" -> "AI agent sozlamalari yangilanmoqda";
            case "list_campaigns" -> "Kampaniyalar ro'yxati tekshirilmoqda";
            case "get_campaign_status" -> "Kampaniya holati tahlil qilinmoqda";
            case "pause_campaign" -> "Kampaniya to'xtatilmoqda";
            case "resume_campaign" -> "Kampaniya ishga tushirilmoqda";
            case "list_calls" -> "Qo'ng'iroqlar tarixi yuklanmoqda";
            case "get_call_details" -> "Qo'ng'iroq tafsilotlari o'rganilmoqda";
            case "get_call_transcript" -> "Qo'ng'iroq dialogi tahlil qilinmoqda";
            case "get_analytics_summary" -> "Umumiy analitika hisoblanmoqda";
            case "get_hourly_call_metrics" -> "Soatlik qo'ng'iroq statistikasi olinmoqda";
            case "get_agent_performance_metrics" -> "Agent samaradorligi baholanmoqda";
            case "get_billing_balance", "get_billing_transactions" -> "Balans va to'lovlar tekshirilmoqda";
            case "list_scenarios", "get_scenario_details" -> "Ssenariy ma'lumotlari ko'rilmoqda";
            case "list_knowledge_bases", "query_knowledge_base" -> "Bilimlar bazasi qidirilmoqda";
            case "list_sip_trunks", "get_sip_trunk_status" -> "SIP trunk holati tekshirilmoqda";
            default -> {
                String formatted = toolName.replace('_', ' ').strip();
                yield (formatted.isEmpty() ? "Amal" : Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1)) + " bajarilmoqda";
            }
        };
    }
}
