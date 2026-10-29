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
import uz.murodjon.robotcallv2.copilot.application.port.input.CopilotUseCase;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotModelPort;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotRepository;
import uz.murodjon.robotcallv2.copilot.application.port.output.CopilotToolInvoker;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotModelReply;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSessionFilter;
import uz.murodjon.robotcallv2.copilot.domain.enums.CopilotMessageRole;
import uz.murodjon.robotcallv2.copilot.domain.service.CopilotInstructions;
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

/**
 * Asks the model one question at a time, with the platform's MCP tools in reach — always in
 * drafts-only mode, whoever asks — and keeps every question, answer and tool call. The reply's
 * tokens are charged to the company like any other AI work.
 */
@Service
public class CopilotService implements CopilotUseCase {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    /** A tool answer longer than this is cut before the model reads it, and before it is stored. */
    static final int MAX_TOOL_ANSWER_CHARS = 12_000;

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

        RecordingToolInvoker invoker = new RecordingToolInvoker(companyId, session.id());
        CopilotModelReply reply = modelPort.generateReply(CopilotInstructions.forDate(today(companyId)), history,
                mcpServerUseCase.findServerTools(), invoker);

        CopilotMessage answer = repository.createMessage(companyId, CopilotMessage.assistant(session.id(), reply));
        long chargedUzs = callBillingUseCase.chargeCopilotReply(companyId, answer.id(),
                new CallUsage(0, reply.promptTokens(), reply.completionTokens(), 0, 0));
        repository.updateMessageCharge(answer.id(), chargedUzs);
        repository.touchSession(session.id());
        return new CopilotReplyResponse(session.id(), withCharge(answer, chargedUzs), invoker.calls);
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
        } catch (IOException e) {
            log.warn("Failed to send initial session event for copilot session {}", session.id(), e);
        }

        List<McpTool> tools = mcpServerUseCase.findServerTools();

        AtomicBoolean cancelled = new AtomicBoolean(false);
        Thread task = Thread.ofVirtual().name("copilot-ask-" + session.id())
                .start(currentUser.wrap(() -> {
            try {
                StreamingToolInvoker invoker = new StreamingToolInvoker(companyId, session.id(), sink, cancelled);
                CopilotModelReply reply = modelPort.generateReply(CopilotInstructions.forDate(today(companyId)), history,
                        tools, invoker);

                if (cancelled.get()) {
                    return;
                }

                CopilotMessage answer = repository.createMessage(companyId, CopilotMessage.assistant(session.id(), reply));
                long chargedUzs = callBillingUseCase.chargeCopilotReply(companyId, answer.id(),
                        new CallUsage(0, reply.promptTokens(), reply.completionTokens(), 0, 0));
                repository.updateMessageCharge(answer.id(), chargedUzs);
                repository.touchSession(session.id());

                CopilotMessage finalAnswer = withCharge(answer, chargedUzs);

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
        });

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

    /** The last questions and answers; tool rows stay out — the answers already say what they found. */
    private List<CopilotMessage> findRecentConversation(long sessionId) {
        List<CopilotMessage> said = repository.findMessagesBySessionId(sessionId).stream()
                .filter(message -> message.role() != CopilotMessageRole.TOOL)
                .toList();
        int keep = copilotProperties.maxHistory();
        return said.size() > keep ? said.subList(said.size() - keep, said.size()) : said;
    }

    private LocalDate today(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return LocalDate.now(config != null ? clock.withZone(config.zoneId()) : clock);
    }

    private static CopilotMessage withCharge(CopilotMessage message, long chargedUzs) {
        return new CopilotMessage(message.id(), message.sessionId(), message.role(), message.content(),
                message.toolName(), message.toolArguments(), message.toolError(), message.promptTokens(),
                message.completionTokens(), chargedUzs, message.createdAt());
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
     * Runs the model's tool calls in drafts-only mode, stores each as it happens — so a reply
     * that fails half-way still leaves its trail — and stops the model past the call budget.
     */
    private final class RecordingToolInvoker implements CopilotToolInvoker {

        private final long companyId;
        private final long sessionId;
        private final List<CopilotMessage> calls = new ArrayList<>();

        private RecordingToolInvoker(long companyId, long sessionId) {
            this.companyId = companyId;
            this.sessionId = sessionId;
        }

        @Override
        public String callTool(String name, String argumentsJson) {
            if (calls.size() >= copilotProperties.maxToolCalls()) {
                return "TOOL_BUDGET_EXHAUSTED: answer the user now with what you already have.";
            }
            String answer;
            boolean error = false;
            try {
                answer = mcpServerUseCase.callServerToolAsDraftOnly(companyId, name, argumentsJson);
            } catch (AppException e) {
                answer = e.code().name() + ": " + e.getMessage();
                error = true;
            } catch (RuntimeException e) {
                log.warn("Copilot tool {} failed for company {}", name, companyId, e);
                answer = ErrorCode.INTERNAL_SERVER_ERROR.name() + ": the tool failed";
                error = true;
            }
            if (answer.length() > MAX_TOOL_ANSWER_CHARS) {
                answer = answer.substring(0, MAX_TOOL_ANSWER_CHARS)
                        + "\n…[cut: the answer was longer — ask for a smaller page or one item]";
            }
            calls.add(repository.createMessage(companyId,
                    CopilotMessage.tool(sessionId, name, argumentsJson, answer, error)));
            return answer;
        }
    }

    /**
     * Streaming invoker that notifies the EventSink in real-time on every tool start and result.
     */
    private final class StreamingToolInvoker implements CopilotToolInvoker {

        private final long companyId;
        private final long sessionId;
        private final EventSink sink;
        private final AtomicBoolean cancelled;
        private final List<CopilotMessage> calls = new ArrayList<>();

        private StreamingToolInvoker(long companyId, long sessionId, EventSink sink, AtomicBoolean cancelled) {
            this.companyId = companyId;
            this.sessionId = sessionId;
            this.sink = sink;
            this.cancelled = cancelled;
        }

        @Override
        public String callTool(String name, String argumentsJson) {
            if (cancelled.get()) {
                return "CANCELLED";
            }
            if (calls.size() >= copilotProperties.maxToolCalls()) {
                return "TOOL_BUDGET_EXHAUSTED: answer the user now with what you already have.";
            }

            try {
                sink.send("tool_call", Map.of("toolName", name, "arguments", argumentsJson));
            } catch (IOException ignored) {
            }

            String answer;
            boolean error = false;
            try {
                answer = mcpServerUseCase.callServerToolAsDraftOnly(companyId, name, argumentsJson);
            } catch (AppException e) {
                answer = e.code().name() + ": " + e.getMessage();
                error = true;
            } catch (RuntimeException e) {
                log.warn("Copilot tool {} failed for company {}", name, companyId, e);
                answer = ErrorCode.INTERNAL_SERVER_ERROR.name() + ": the tool failed";
                error = true;
            }
            if (answer.length() > MAX_TOOL_ANSWER_CHARS) {
                answer = answer.substring(0, MAX_TOOL_ANSWER_CHARS)
                        + "\n…[cut: the answer was longer — ask for a smaller page or one item]";
            }
            CopilotMessage toolMsg = repository.createMessage(companyId,
                    CopilotMessage.tool(sessionId, name, argumentsJson, answer, error));
            calls.add(toolMsg);

            try {
                sink.send("tool_result", toolMsg);
            } catch (IOException ignored) {
            }

            return answer;
        }
    }
}
