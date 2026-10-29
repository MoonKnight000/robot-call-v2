package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.secret.domain.service.SecretPlaceholders;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.http.PublicHttpClient;
import uz.murodjon.robotcallv2.shared.http.PublicHttpRequest;
import uz.murodjon.robotcallv2.shared.http.PublicHttpResponse;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolExecutionUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolExecution;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolKeyValuePair;
import uz.murodjon.robotcallv2.tool.domain.enums.ToolEffect;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Calls the REST endpoints a company configured as tools, on behalf of the model.
 *
 * <p>The company's secrets are read once per call and substituted into the URL and the
 * headers ({@link ToolRequestTemplates}). Two rules follow from that and are the reason
 * this code does its own placeholder handling instead of asking the service per string:
 * secrets are resolved <em>before</em> anything the caller or the model supplied, so a value carrying
 * {@code {{secrets.X}}} cannot make the request fetch a credential; and every string that
 * leaves here for a log or for the model goes through
 * {@link SecretPlaceholders#redact(String, java.util.Collection)} first, because a
 * resolved URL is the credential.
 *
 * <p>What comes back is treated as text this system did not write ({@link ToolResponseFormatter}),
 * and a write tool waits for the caller's yes ({@link ToolConfirmation}). This class runs the
 * call itself: the per-call limit, the address guard, auth, retries and the execution log.
 */
@Component
public class HttpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpToolExecutor.class);
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /** What a tool with no canned answer returns in a simulation (buildMockCallback). */
    static final String MOCK_NOT_PROVIDED = "{\"error\":\"service unavailable\"}";

    /**
     * How much of the answer is read at all. An endpoint that streams a gigabyte would
     * otherwise be read into memory on a call thread, and none of it is a tool result.
     */
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    /** Pause before the first retry; the second waits twice as long. */
    private static final long RETRY_BACKOFF_MS = 300;

    /** Stands in for a conversation when a tool is run from the panel's "Test" button. */
    private static final DialogOutcomeSink DRY_RUN = new DryRunSink();

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Arguments written with every object's keys in order, so the same call is the same string. */
    private final ObjectWriter canonicalWriter;

    private final PublicHttpClient httpClient;
    private final SecretUseCase secretUseCase;
    private final ToolExecutionUseCase toolExecutionUseCase;
    private final ToolOAuthTokens oauthTokens;
    private final ToolSchemas toolSchemas;
    private final ToolRequestTemplates toolRequestTemplates;
    private final ToolResponseFormatter toolResponseFormatter;
    private final ToolConfirmation toolConfirmation;

    public HttpToolExecutor(SecretUseCase secretUseCase, ToolExecutionUseCase toolExecutionUseCase,
                            ToolOAuthTokens oauthTokens, ToolSchemas toolSchemas,
                            ToolRequestTemplates toolRequestTemplates, ToolResponseFormatter toolResponseFormatter,
                            ToolConfirmation toolConfirmation, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.toolSchemas = toolSchemas;
        this.toolRequestTemplates = toolRequestTemplates;
        this.toolResponseFormatter = toolResponseFormatter;
        this.toolConfirmation = toolConfirmation;
        this.secretUseCase = secretUseCase;
        this.toolExecutionUseCase = toolExecutionUseCase;
        this.oauthTokens = oauthTokens;
        // The shared client: the host is resolved only to public addresses at connect time,
        // and redirects are never followed — PublicUrlGuard screens the address that was
        // configured, and a 302 is the endpoint picking a second one after that check has
        // passed, which is exactly how such a filter is walked around.
        this.httpClient = PublicHttpClient.create(Duration.ofSeconds(10), Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));
    }

    public ToolCallback buildCallback(Tool tool, DialogOutcomeSink session, Map<String, Object> callVariables) {
        String toolName = sanitizeToolName(tool.name());
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(toolName, args -> execute(tool, session, callVariables, args))
                .description(tool.requiresConfirmation() ? tool.description() + ToolConfirmation.CONFIRMATION_HINT : tool.description())
                .inputSchema(toolSchemas.generateInputSchema(tool))
                .inputType(Map.class)
                .build();
    }

    /**
     * The same tool as {@link #buildCallback} offers the model — name, description, input
     * schema — answering from the simulation's canned responses instead: nothing is sent, no
     * secret is read, no execution is logged. A tool with no canned answer says it is
     * unavailable, so the agent's handling of a failed lookup is tested too.
     */
    public ToolCallback buildMockCallback(Tool tool, DialogSession session) {
        String toolName = sanitizeToolName(tool.name());
        return FunctionToolCallback
                .<Map<String, Object>, String>builder(toolName, args -> {
                    session.recordSimulatedToolCall("TOOL: " + tool.name() + " " + canonical(args));
                    String answer = session.toolMocks().get(tool.name());
                    return answer != null ? answer : MOCK_NOT_PROVIDED;
                })
                .description(tool.description())
                .inputSchema(toolSchemas.generateInputSchema(tool))
                .inputType(Map.class)
                .build();
    }

    /**
     * One run of {@code tool} outside any call — the panel's "Test" button. Same request,
     * same address guard, same answer the model would get; there is simply no conversation
     * for the tool to act on, and no per-call limit to count against.
     */
    public String executeForTest(Tool tool, Map<String, Object> callVariables, Map<String, Object> args) {
        return execute(tool, DRY_RUN, callVariables != null ? callVariables : Map.of(), args != null ? args : Map.of());
    }

    public String execute(Tool tool, DialogOutcomeSink session, Map<String, Object> callVariables, Map<String, Object> args) {
        String canonicalArgs = canonical(args);
        // A write the caller has not said yes to is not sent (UNIVERSAL-OPERATOR-PLAN B.3).
        // Held here rather than asked of the prompt: a stranger's voice writing into the
        // company's system is exactly what a prompt cannot be trusted to hold. The panel's
        // test run has no caller to ask.
        if (tool.requiresConfirmation() && session != DRY_RUN) {
            if (!toolConfirmation.isConfirmed(tool, session, canonicalArgs)) {
                return toolConfirmation.holdForConfirmation(tool, session, args, canonicalArgs);
            }
            session.setPendingConfirmation(tool.name(), null);
        }
        // A model stuck in a loop calls the same lookup over and over, and each one is a
        // request to the company's API and a pause the caller sits through.
        int callNumber = session.countToolCall(tool.name());
        if (tool.maxCallsPerCall() > 0 && callNumber > tool.maxCallsPerCall()) {
            log.warn("[{}] Tool '{}' refused: already called {} times on this call",
                    session.channelId(), tool.name(), tool.maxCallsPerCall());
            return toolResponseFormatter.formatLimitReached(tool.maxCallsPerCall());
        }
        if (tool.speech().forcePreToolSpeech() && tool.speech().preToolSpeech() != null && !tool.speech().preToolSpeech().isBlank()) {
            session.addToolReply(tool.speech().preToolSpeech().trim());
        }

        Map<String, String> secrets = tool.companyId() != null
                ? secretUseCase.findDecryptedSecrets(tool.companyId())
                : Map.of();
        Long callAttemptId = session == DRY_RUN ? null : callAttemptId(callVariables);
        String argsHash = ToolExecution.hashArguments(canonicalArgs);
        long started = System.nanoTime();

        try {
            int timeoutSeconds = (tool.http().responseTimeoutSecs() != null && tool.http().responseTimeoutSecs() > 0)
                    ? tool.http().responseTimeoutSecs()
                    : DEFAULT_TIMEOUT_SECONDS;

            String resolvedUrl = toolRequestTemplates.resolveUrl(tool, callVariables, args, secrets);
            // Screened again here, not only when the tool was saved: a path or query value
            // the model supplied has been substituted in by now, and it can point the
            // request somewhere the stored address did not.
            URI uri = PublicUrlGuard.parsePublic(resolvedUrl);
            if (uri == null) {
                log.warn("[{}] Tool '{}' was not called: its address is not publicly callable",
                        session.channelId(), tool.name());
                return toolResponseFormatter.formatBlocked();
            }
            String method = (tool.http().method() != null ? tool.http().method().toUpperCase() : "POST");

            PublicHttpRequest request = PublicHttpRequest.create(method, uri)
                    .setTimeout(Duration.ofSeconds(timeoutSeconds))
                    .setMaxResponseBytes(MAX_RESPONSE_BYTES);

            // Apply Headers
            boolean hasContentType = false;
            if (tool.http().headers() != null) {
                for (ToolKeyValuePair header : tool.http().headers()) {
                    // A tool with its own OAuth client authenticates with the token below.
                    if (header.key() != null && !header.key().isBlank()
                            && !(tool.http().auth() != null && AUTHORIZATION_HEADER.equalsIgnoreCase(header.key().trim()))) {
                        String headerValue = toolRequestTemplates.resolveTemplate(header.value(), callVariables, args, secrets);
                        request.addHeader(header.key().trim(), headerValue);
                        if ("content-type".equalsIgnoreCase(header.key().trim())) {
                            hasContentType = true;
                        }
                    }
                }
            }

            // Fresh or cached (UNIVERSAL-OPERATOR-PLAN B.5). A token that cannot be had fails the
            // call below like any other request that did not go out.
            if (tool.http().auth() != null && tool.companyId() != null) {
                request.addHeader(AUTHORIZATION_HEADER,
                        "Bearer " + oauthTokens.findAccessToken(tool.companyId(), tool.http().auth(), secrets));
            }

            // A retried POST must not become a second order: the key is the same on every
            // retry of one call, so an endpoint that honours it answers the repeat with the first.
            if (tool.effect() == ToolEffect.WRITE && !hasHeader(tool, IDEMPOTENCY_KEY_HEADER)) {
                request.addHeader(IDEMPOTENCY_KEY_HEADER, idempotencyKey(tool, callAttemptId, callNumber));
            }

            // Build Body
            if (!("GET".equals(method) || "DELETE".equals(method))) {
                if (!hasContentType) {
                    request.addHeader("Content-Type", "application/json");
                }
                request.setBody(toolRequestTemplates.buildBody(tool, callVariables, args, secrets));
            }

            log.info("[{}] Executing HTTP tool '{}' {} {}", session.channelId(), tool.name(), method,
                    SecretPlaceholders.redact(resolvedUrl, secrets.values()));
            PublicHttpResponse response = sendWithRetry(tool, session, request);

            log.info("[{}] Tool '{}' responded with status {}", session.channelId(), tool.name(), response.statusCode());
            if (response.statusCode() == 401 && tool.http().auth() != null && tool.companyId() != null) {
                // Refused before it ran out — revoked or rotated. The next call asks for a new one.
                oauthTokens.evictToken(tool.companyId(), tool.http().auth(), secrets);
            }

            // Already capped at MAX_RESPONSE_BYTES; the rest of the stream was never read.
            String responseBody = response.readBodyText();
            if (tool.companyId() != null) {
                toolExecutionUseCase.recordExecution(ToolExecution.answered(tool.companyId(), tool.id(), callAttemptId,
                        tool.name(), response.statusCode(), elapsedMs(started), argsHash,
                        SecretPlaceholders.redact(responseBody, secrets.values())));
            }
            session.recordOutcome("tool_" + tool.name(), Map.of(
                    "status", response.statusCode(),
                    "response", responseBody
            ));
            return toolResponseFormatter.formatForModel(tool, session, response.statusCode(), responseBody);

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                // The realtime engine's tool deadline cancels by interrupting; keep it visible.
                Thread.currentThread().interrupt();
            }
            // The detail stays on the server. A connection or URI failure names the address
            // it failed on, and that address has the credential in it by this point — the
            // model has no use for it and repeats what it is told out loud.
            String reason = SecretPlaceholders.redact(String.valueOf(e.getMessage()), secrets.values());
            log.error("[{}] Tool '{}' execution failed: {}", session.channelId(), tool.name(), reason);
            if (tool.companyId() != null) {
                toolExecutionUseCase.recordExecution(ToolExecution.failed(tool.companyId(), tool.id(), callAttemptId,
                        tool.name(), elapsedMs(started), argsHash, reason));
            }
            if (session == DRY_RUN) {
                // A test run from the panel: the one reading the result owns the tool, and a
                // failure whose reason they cannot see is no test at all.
                throw new ExternalServiceException(ErrorCode.TOOL_EXECUTION_FAILED, "tool", e, tool.name(), reason);
            }
            return toolResponseFormatter.formatFailed();
        }
    }

    /**
     * Sends the request, and again after a 5xx or a network failure up to the tool's
     * {@code retryAttempts}. A 4xx is never retried: the same request gets the same answer.
     */
    private PublicHttpResponse sendWithRetry(Tool tool, DialogOutcomeSink session, PublicHttpRequest request)
            throws IOException, InterruptedException {
        int retries = tool.http().retryAttempts();
        for (int attempt = 0; ; attempt++) {
            try {
                PublicHttpResponse response = httpClient.send(request);
                if (response.statusCode() < 500 || attempt >= retries) {
                    return response;
                }
                log.warn("[{}] Tool '{}' answered {}, retrying ({}/{})", session.channelId(), tool.name(),
                        response.statusCode(), attempt + 1, retries);
            } catch (IOException e) {
                if (attempt >= retries) {
                    throw e;
                }
                log.warn("[{}] Tool '{}' failed ({}), retrying ({}/{})", session.channelId(), tool.name(),
                        e.getClass().getSimpleName(), attempt + 1, retries);
            }
            Thread.sleep(RETRY_BACKOFF_MS * (attempt + 1));
        }
    }

    /** The arguments as canonical JSON: what a confirmation covers and what the log hashes. */
    private String canonical(Map<String, Object> args) {
        try {
            return canonicalWriter.writeValueAsString(args == null ? Map.of() : args);
        } catch (Exception e) {
            return String.valueOf(args);
        }
    }

    /** The call attempt a live call runs under ({@code TurnTools} puts it there); null when there is none. */
    private static Long callAttemptId(Map<String, Object> callVariables) {
        Object id = callVariables == null ? null : callVariables.get("call_attempt_id");
        return id instanceof Number number && number.longValue() > 0 ? number.longValue() : null;
    }

    private static boolean hasHeader(Tool tool, String name) {
        return tool.http().headers().stream()
                .anyMatch(header -> header.key() != null && header.key().trim().equalsIgnoreCase(name));
    }

    /**
     * The same on every retry of one call, different for the next call: the call attempt,
     * the tool, and how many times this call has used it. A test run from the panel has no
     * call attempt and gets a fresh key each time — a tester pressing the button twice means it.
     */
    private static String idempotencyKey(Tool tool, Long callAttemptId, int callNumber) {
        String call = callAttemptId != null ? callAttemptId.toString() : "test-" + UUID.randomUUID();
        return call + ":" + sanitizeToolName(tool.name()) + ":" + callNumber;
    }

    private static int elapsedMs(long startedNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public static String sanitizeToolName(String name) {
        if (name == null || name.isBlank()) {
            return "custom_tool";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * A conversation that is not there: the panel's test run. The tool may still say its
     * pre-tool line and record an outcome; neither has anywhere to go.
     */
    private static final class DryRunSink implements DialogOutcomeSink {

        @Override
        public String channelId() {
            return "tool-test";
        }

        @Override
        public void addToolReply(String reply) {
        }

        @Override
        public ScenarioDefinition scenario() {
            return null;
        }

        @Override
        public String state() {
            return null;
        }

        @Override
        public void setState(String state) {
        }

        @Override
        public void recordOutcome(String key, Object value) {
        }

        @Override
        public Map<String, Object> outcome() {
            return Map.of();
        }

        @Override
        public void setDisposition(Disposition disposition) {
        }

        @Override
        public void end(Disposition disposition) {
        }

        @Override
        public void setDoNotCallReason(String reason) {
        }

        @Override
        public void setTransferReason(String reason) {
        }

        @Override
        public boolean sendDtmf(String digits) {
            return false;
        }

        /** No count is kept: every test call is its first. */
        @Override
        public int countToolCall(String toolName) {
            return 1;
        }

        @Override
        public void waitForCaller() {
        }

        @Override
        public boolean switchCallLanguage(String language) {
            return false;
        }

        @Override
        public void noteCallerMood(CustomerSentiment mood) {
        }

        @Override
        public AiAgent agent() {
            return null;
        }

        @Override
        public CallContext context() {
            return null;
        }

        /** No fact guard runs behind a test call. */
        @Override
        public void recordToolFigures(Collection<BigDecimal> figures) {
        }

        /** Never asked: {@link #execute} skips the confirmation for a dry run. */
        @Override
        public PendingToolConfirmation findPendingConfirmation(String toolName) {
            return null;
        }

        @Override
        public void setPendingConfirmation(String toolName, PendingToolConfirmation pending) {
        }

        @Override
        public int countCallerUtterances() {
            return 0;
        }

        @Override
        public String findLastCallerText() {
            return null;
        }

        @Override
        public void rememberCatalogResult(String result) {
        }

        @Override
        public boolean triggerMidCallSms(String text) {
            return false;
        }

        @Override
        public boolean handOffToAgent(long agentId) {
            return false;
        }

        @Override
        public boolean isWebhookEventEnabled(AgentWebhookEvent event) {
            return false;
        }

        @Override
        public void publishWebhookEvent(AgentWebhookEvent event, Map<String, Object> data) {
        }

        @Override
        public boolean isEnded() {
            return false;
        }
    }
}
