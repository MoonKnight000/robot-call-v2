package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.RealtimeMetrics;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProperties;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeSession;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.CatalogSearchUseCase;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.tool.application.port.input.ToolUseCase;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The tools of a call running on a speech-to-speech engine: declared once at connect,
 * refused or run as the engine asks for them, and answered back to it under a deadline.
 */
@Component
class RealtimeToolRunner {

    private static final Logger log = LoggerFactory.getLogger(RealtimeToolRunner.class);

    /** The longest tool name OpenAI and Gemini accept in a declaration. */
    private static final int MAX_TOOL_NAME_LENGTH = 64;

    /** Appended to a tool answer the engine gets after it already spoke this turn — see {@link #runTool}. */
    private static final String ALREADY_SPOKEN = " [SYSTEM: the client has already heard what you said this turn — "
            + "do not say it again. If it already ended with a question to the client, stay silent and wait for "
            + "the answer; otherwise ask the current stage's question now, in one short sentence.]";

    /** One second at the 8 kHz the line plays: less than that is a breath or a syllable, not a line ({@link #hasSpokenALine}). */
    private static final long MIN_LINE_SAMPLES = 8000;

    private final KnowledgeRetrievalUseCase knowledgeRetrieval;
    private final CatalogSearchUseCase catalogSearch;
    private final RealtimeProperties realtimeProperties;
    private final RealtimeMetrics metrics;
    private final StageScriptRunner stageScriptRunner;
    private final ClientMemoryUseCase clientMemory;
    private final IntegrationHttpToolFactory integrationHttpToolFactory;
    private final ToolUseCase toolUseCase;
    private final HttpToolExecutor httpToolExecutor;
    private final McpToolCallbackFactory mcpToolCallbackFactory;

    /**
     * Tool bodies hit the database and the CRM, and they arrive on the engine's network
     * thread — the same thread its next audio frame comes in on. Virtual threads because
     * the work is all waiting, and there is one per tool call, not one per call.
     */
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

    RealtimeToolRunner(KnowledgeRetrievalUseCase knowledgeRetrieval, CatalogSearchUseCase catalogSearch,
                       RealtimeProperties realtimeProperties, RealtimeMetrics metrics,
                       StageScriptRunner stageScriptRunner, ClientMemoryUseCase clientMemory,
                       IntegrationHttpToolFactory integrationHttpToolFactory, ToolUseCase toolUseCase,
                       HttpToolExecutor httpToolExecutor, McpToolCallbackFactory mcpToolCallbackFactory) {
        this.knowledgeRetrieval = knowledgeRetrieval;
        this.catalogSearch = catalogSearch;
        this.realtimeProperties = realtimeProperties;
        this.metrics = metrics;
        this.stageScriptRunner = stageScriptRunner;
        this.clientMemory = clientMemory;
        this.integrationHttpToolFactory = integrationHttpToolFactory;
        this.toolUseCase = toolUseCase;
        this.httpToolExecutor = httpToolExecutor;
        this.mcpToolCallbackFactory = mcpToolCallbackFactory;
    }

    /**
     * Every tool this call's scenario allows, declared once at connect.
     */
    List<ToolCallback> buildTools(RealtimeDialogSession s, boolean factsInPrompt) {
        Set<String> declared = new HashSet<>();
        if (s.scenario().tools() != null) {
            s.scenario().tools().forEach(t -> declared.add(t.name()));
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        if (!factsInPrompt) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealtimeFactTools(s)).build().getToolCallbacks()));
        }
        // Only for agents that asked for it: an engine offered a tool it never needs still
        // pays for the description in every request, and may call it out of curiosity. The tools
        // are fixed at connect, so a stage that switches the knowledge base on by itself (own
        // sources, or an override) counts too; refuse() holds the search back in the stages where
        // it is off, the way the cascade rebuilds its list per turn.
        if (s.agent() != null && (s.agent().useRag() || StageEntry.anyStageUsesKnowledge(s.scenario()))) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealtimeKnowledgeTools(s, knowledgeRetrieval))
                    .build().getToolCallbacks()));
        }
        // The catalog search, for an agent that has a catalog (UNIVERSAL-OPERATOR-PLAN B.4).
        // Not tied to useRag: an order cannot be placed without the SKU it finds.
        Long agentId = s.agent() != null ? s.agent().id() : null;
        if (catalogSearch.existsByCompanyIdAndAgentId(s.companyId(), agentId)) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new CatalogTools(s, s.companyId(), agentId, catalogSearch, clientMemory))
                    .build().getToolCallbacks()));
        }
        // The HTTP tools the platform admin defined for the company's CRM; declared once, with the token they carry.
        if (integrationHttpToolFactory != null) {
            callbacks.addAll(integrationHttpToolFactory.build(s, s.companyId(),
                    ToolCallVariables.build(s.channelId(), s.companyId(), s.language(), s.callAttemptId(), s.context())));
        }
        for (ToolCallback fixed : MethodToolCallbackProvider.builder()
                .toolObjects(new DialogTools(s, stageScriptRunner)).build().getToolCallbacks()) {
            String name = fixed.getToolDefinition().name();
            if ("sendMidCallSms".equals(name)) {
                if (AgentFunctionPlan.offersSms(s.agent())) {
                    callbacks.add(fixed);
                }
                continue;
            }
            if ("transferToAgent".equals(name)) {
                if (AgentFunctionPlan.offersAgentTransfer(s.agent())) {
                    callbacks.add(fixed);
                }
                continue;
            }
            if ("endCall".equals(name) && !AgentFunctionPlan.offersEndCall(s.agent())) {
                continue; // the agent's function list has no END_CALL: it does not hang up by itself
            }
            if ("sendDtmfTones".equals(name) && DialogTools.isPressDigitEnabled(s)) {
                callbacks.add(fixed); // the agent presses keys itself, whatever its scenario declares
                continue;
            }
            // Declared once at connect, so this holds the tool back only when no stage could ever
            // have a target; the stage-by-stage case is refuse()'s.
            if ("requestHumanTransfer".equals(name)
                    && !TransferTargets.isOfferedAnywhere(s.agent(), s.isTransferTargetAvailable(), s.scenario())) {
                continue;
            }
            if (DialogTools.CASCADE_ONLY_TOOL_NAMES.contains(name)) {
                continue;
            }
            if ("transitionTo".equals(name) && StageEntry.hasNowhereToTransition(s.scenario())) {
                continue; // a single-prompt agent has no stage to move to
            }
            // The same rule the cascade pipeline applies (TurnTools#build), and it has to
            // be the same rule: the previous condition here included a tool when it was
            // NOT one of the hardcoded ones, which is the opposite set. It left every
            // realtime call without endCall, requestHumanTransfer, recordWrongPerson or
            // recordDoNotCall — a bot that could not hang up, escalate, or back out of a
            // call it had reached the wrong person on.
            if (DialogTools.ALWAYS_AVAILABLE_TOOL_NAMES.contains(name) || declared.contains(name)) {
                callbacks.add(fixed);
            }
        }
        if (s.scenario().tools() != null) {
            for (ToolDef toolDef : s.scenario().tools()) {
                if (!DialogTools.HARDCODED_TOOL_NAMES.contains(toolDef.name())) {
                    callbacks.add(ScenarioToolCallbackFactory.build(toolDef, s));
                }
            }
        }
        // Declared once for every EXTRACT_VARIABLE stage of the flow, used only inside one (refuse).
        ToolDef extraction = StageEntry.findExtractionToolForFlow(s.scenario());
        if (extraction != null) {
            callbacks.add(ScenarioToolCallbackFactory.build(extraction, s));
        }
        // The agent's EXTRACT_VARIABLE functions, usable at every stage like its other function tools.
        addDeclarable(callbacks, AgentFunctionTools.buildExtractionTools(s.agent(), s, s.companyId(), clientMemory), s);
        // The agent's own HTTP tools and its MCP servers' tools: usable at every stage, as on the
        // cascade pipeline (refuse() lets them through).
        List<Tool> agentTools = findAgentTools(s);
        addDeclarable(callbacks, buildAgentHttpTools(s, agentTools), s);
        addDeclarable(callbacks, buildMcpTools(s), s);
        return AgentFunctionTools.describe(callbacks, s.agent(), agentTools);
    }

    /** The tools attached to the call's agent; empty for a call with no stored agent, and when they cannot be read. */
    private List<Tool> findAgentTools(RealtimeDialogSession s) {
        if (s.agent() == null || s.agent().id() == 0L) {
            return List.of();
        }
        try {
            return toolUseCase.findAgentToolDefinitions(s.companyId(), s.agent().id());
        } catch (Exception e) {
            log.warn("[{}] failed to load active tools for agent {}: {}", s.channelId(), s.agent().id(), e.getMessage());
            return List.of();
        }
    }

    /**
     * The HTTP tools attached to the call's agent. A tool that waits for the caller's yes before a
     * write is left out: that confirmation counts the caller's transcribed lines, which a
     * speech-to-speech engine delivers after the fact, so the yes could never be recognised.
     * Never throws: a call goes on without them.
     */
    private List<ToolCallback> buildAgentHttpTools(RealtimeDialogSession s, List<Tool> agentTools) {
        try {
            Map<String, Object> variables = ToolCallVariables.build(s.channelId(), s.companyId(), s.language(),
                    s.callAttemptId(), s.context());
            List<ToolCallback> callbacks = new ArrayList<>();
            for (Tool tool : agentTools) {
                if (!HttpToolExecutor.isOfferedToModel(tool) || !AgentFunctionPlan.offersCustomTool(s.agent(), tool)) {
                    continue;
                }
                if (tool.requiresConfirmation()) {
                    log.info("[{}] tool {} needs the caller's confirmation, which realtime calls cannot take - not offered",
                            s.channelId(), tool.name());
                    continue;
                }
                callbacks.add(httpToolExecutor.buildCallback(tool, s, variables));
            }
            return callbacks;
        } catch (Exception e) {
            log.warn("[{}] failed to load active tools for agent {}: {}", s.channelId(), s.agent().id(), e.getMessage());
            return List.of();
        }
    }

    /** The MCP tools of the servers attached to the call's agent; the factory never throws. */
    private List<ToolCallback> buildMcpTools(RealtimeDialogSession s) {
        return mcpToolCallbackFactory.build(s, s.companyId(), s.callAttemptId());
    }

    /**
     * Adds {@code candidates} to what is declared to the engine, skipping a tool the engine would
     * refuse the whole declaration for: a name already taken (a scenario tool, or two tools
     * sanitised to the same name) or longer than the 64 characters OpenAI and Gemini allow.
     */
    private static void addDeclarable(List<ToolCallback> callbacks, List<ToolCallback> candidates,
                                      RealtimeDialogSession s) {
        Set<String> taken = new HashSet<>();
        callbacks.forEach(callback -> taken.add(callback.getToolDefinition().name()));
        for (ToolCallback candidate : candidates) {
            String name = candidate.getToolDefinition().name();
            if (name.length() > MAX_TOOL_NAME_LENGTH || !taken.add(name)) {
                log.warn("[{}] tool {} not offered to the realtime engine: duplicate or over {} characters",
                        s.channelId(), name, MAX_TOOL_NAME_LENGTH);
                continue;
            }
            callbacks.add(candidate);
        }
    }

    /**
     * The tool runs on its own thread with a deadline. Whichever finishes first — the
     * tool or the deadline — answers the engine and releases the in-flight count,
     * exactly once; the loser finds {@code answered} already set and does nothing.
     *
     * <p>Called on the engine's thread, in order with its audio, and everything up to the
     * submit happens there.
     */
    void handleToolCall(RealtimeDialogSession session, String callId, String name, String argumentsJson) {
        session.touch();
        session.toolsInFlight().incrementAndGet();
        // Read here, on the engine's thread, in order with its audio: by the time the
        // tool runs a turnComplete may already have closed the reply.
        boolean spokeThisTurn = hasSpokenALine(session);
        long started = System.nanoTime();
        AtomicBoolean answered = new AtomicBoolean();
        Runnable release = () -> {
            session.toolsInFlight().decrementAndGet();
            metrics.recordRealtimeToolLatency(name, Duration.ofNanos(System.nanoTime() - started));
        };
        Future<?> running = toolExecutor.submit(() -> CallMdc.with(session.channelId(), () -> {
            String result = runTool(session, name, argumentsJson, spokeThisTurn);
            if (answered.compareAndSet(false, true)) {
                answerTool(session, callId, result);
                release.run();
            }
        }));
        long timeoutMs = Math.max(1, realtimeProperties.toolTimeoutSeconds()) * 1000L;
        // A parked virtual thread is the cheapest timer there is.
        toolExecutor.execute(() -> CallMdc.with(session.channelId(), () -> {
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!answered.compareAndSet(false, true)) {
                return;
            }
            running.cancel(true);
            log.warn("[{}] tool {} did not answer within {} ms — telling the engine it failed",
                    session.channelId(), name, timeoutMs);
            answerTool(session, callId, "ERROR: the data source did not answer (" + timeoutMs / 1000
                    + " seconds). Apologise to the client and carry on with the conversation.");
            release.run();
        }));
    }

    /**
     * What the engine is told for this call — a refusal, the tool's answer, or its error.
     *
     * <p>A tool answer makes the engine generate again. When it had already spoken this
     * turn before calling, it tends to say the same line over: on a recorded call the
     * greeting and a question were each heard twice. {@link #ALREADY_SPOKEN} rides along
     * with every such answer, because a sync-only engine (Gemini 3.1 Flash Live) has no
     * way to take a tool answer silently.
     */
    private String runTool(RealtimeDialogSession s, String name, String argumentsJson, boolean spokeThisTurn) {
        String refusal = refuse(s, name, spokeThisTurn);
        String result;
        if (refusal != null) {
            log.warn("[{}] refused tool {} in {} on turn {}", s.channelId(), name, s.state(), s.turnCount());
            result = refusal;
        } else {
            result = execute(s, name, argumentsJson);
        }
        return spokeThisTurn ? result + ALREADY_SPOKEN : result;
    }

    /**
     * Whether the open reply has put a real line on the wire, not just a breath. On a recorded
     * call the engine played half a second of "{pause}" before calling transitionTo; counted as
     * spoken, the answer carried {@link #ALREADY_SPOKEN}, the engine went silent for nine seconds
     * and the caller heard the bot cut itself off.
     */
    private static boolean hasSpokenALine(RealtimeDialogSession s) {
        if (!s.replyOpen()) {
            return false;
        }
        RtpEndpoint endpoint = s.endpoint();
        return endpoint == null || endpoint.queuedSamples() - s.replyStartSample() >= MIN_LINE_SAMPLES;
    }

    private static void answerTool(RealtimeDialogSession s, String callId, String result) {
        RealtimeSession engine = s.engine();
        if (engine != null) {
            engine.sendToolResult(callId, result);
        }
    }

    private String execute(RealtimeDialogSession s, String name, String argumentsJson) {
        try {
            ToolCallback callback = s.tools().stream()
                    .filter(t -> t.getToolDefinition().name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (callback == null) {
                log.warn("[{}] engine called unknown tool {}", s.channelId(), name);
                return "ERROR: no such tool";
            }
            String result = callback.call(argumentsJson);
            if (!result.startsWith("ERROR:")) {
                s.publishWebhookEvent(AgentWebhookEvent.TOOL_EXECUTED, Map.of("toolName", name));
            }
            log.info("[{}] tool {} -> {}", s.channelId(), name, result);
            return result;
        } catch (Exception e) {
            // The detail stays in the log: an exception message is the engine's to read aloud otherwise.
            log.warn("[{}] tool {} failed: {}", s.channelId(), name, e.getMessage());
            return "ERROR: " + name + " failed";
        }
    }

    /**
     * Why this call may not run right now, or null to let it through.
     *
     * <p>The cascade pipeline rebuilds its tool list before every turn, so a tool the
     * current stage does not allow is simply never offered ({@code
     * TurnTools#allowedTools}). A realtime engine is handed its tools once at connect and
     * keeps them for the whole call, so the same rules have nowhere to live but here, on
     * the way in — and the answer goes back as a tool result, which is the one channel
     * the model is guaranteed to read mid-turn.
     */
    private static String refuse(RealtimeDialogSession s, String name, boolean spokeThisTurn) {
        // Nothing may move the call off its opening stage before the caller has been
        // greeted. The engine *starts* in that stage rather than transitioning into it,
        // so the prompt's "transition first, then speak" rule reads there as "leave
        // immediately": on a recorded call the greeting, the introduction and the §11.1
        // disclosure were never spoken at all — the first thing the caller heard was
        // "am I speaking to Murodjon?".
        // Only while nothing has been said, though. Refusing a transition that came after
        // the greeting told the engine to greet again — the caller heard it twice — and
        // left the call in the opening stage for good, every later transition and outcome
        // refused from there. A greeting cut short is still caught by checkDisclosure.
        if ("transitionTo".equals(name) && s.turnCount() == 0 && !spokeThisTurn) {
            return "ERROR: first greet the client and introduce yourself and the company. "
                    + "You change the stage only after you have said your first line.";
        }
        // A promised date the caller has not had the chance to give. The model asked for
        // the stage and the promise in one breath, before its own question had been
        // spoken — on the recorded call that turned the caller's "alo" into a
        // 1 500 000 so'm promise for today, and PROMISE_TO_PAY into the CRM. Refusing is
        // recoverable: the model can confirm the date aloud and call again next turn.
        if (givesPromiseToPay(s, name) && s.stageJustEntered()) {
            return "ERROR: you have only just entered this stage — the client has not named a date yet. "
                    + "First say this stage's line and ask the client for the exact day.";
        }
        // Declared once for the whole call, so whether a target exists in THIS stage is decided
        // here — with the rule the cascade applies per turn (TurnTools#build).
        if ("requestHumanTransfer".equals(name)
                && !TransferTargets.isOffered(s.agent(), s.isTransferTargetAvailable(), s.scenario(), s.state())) {
            return "ERROR: there is no operator to hand this call to. Tell the client you cannot connect them "
                    + "and carry on helping, or say a polite goodbye.";
        }
        if (DialogTools.ALWAYS_AVAILABLE_TOOL_NAMES.contains(name)) {
            return null;
        }
        // Key presses are the agent's own setting, not a scenario tool a stage may fence off.
        if ("sendDtmfTones".equals(name) && DialogTools.isPressDigitEnabled(s)) {
            return null;
        }
        StageDef current = s.scenario() == null ? null : s.scenario().findStage(s.state());
        // Declared once at connect for the whole call, so whether the search is on in THIS stage
        // is decided here - with the rule the cascade applies per turn (TurnRunner).
        if (RealtimeKnowledgeTools.SEARCH_TOOL_NAME.equals(name) && !StageEntry.isKnowledgeEnabled(s.agent(), current)) {
            return "ERROR: the knowledge base is not available in this stage.";
        }
        if (StageEntry.EXTRACT_TOOL_NAME.equals(name)) {
            return current != null && current.type() == StageType.EXTRACT_VARIABLE ? null
                    : "ERROR: " + name + " can only be called in a stage that reads values out of the conversation.";
        }
        // A FUNCTION stage exists to call its tool, whatever its tool list says.
        if (current != null && current.type() == StageType.FUNCTION && current.action() != null
                && name.equals(current.action().toolName())) {
            return null;
        }
        // A stage's list governs the scenario's own tools and nothing else. getFact, the
        // knowledge lookup and sendMidCallSms are infrastructure the model needs wherever
        // the call happens to be, and a scenario written before them cannot have named
        // them — gating those on the list would leave the model unable to look up the
        // very figures it is about to say.
        if (!isScenarioTool(s, name)) {
            return null;
        }
        StageDef stage = s.scenario() == null ? null : s.scenario().findStage(s.state());
        // null = the stage names no restriction, every scenario tool is usable here (the
        // common case); an explicit — possibly empty — list means exactly those tools.
        List<String> allowed = StageEntry.findAllowedTools(s.scenario(), stage);
        if (allowed == null || allowed.contains(name)) {
            return null;
        }
        return "ERROR: " + name + " cannot be called in stage " + s.state()
                + (allowed.isEmpty() ? "" : " — only " + String.join(", ", allowed) + " can be called here")
                + ".";
    }

    /** Whether {@code name} is a scenario tool whose call files a promise to pay. */
    private static boolean givesPromiseToPay(RealtimeDialogSession s, String name) {
        if (s.scenario() == null || s.scenario().tools() == null) {
            return false;
        }
        return s.scenario().tools().stream().anyMatch(tool -> name.equals(tool.name())
                && Disposition.PROMISE_TO_PAY.name().equals(tool.disposition()));
    }

    /** Whether this call's scenario is the thing that put {@code name} on the table. */
    private static boolean isScenarioTool(RealtimeDialogSession s, String name) {
        if (s.scenario() == null || s.scenario().tools() == null) {
            return false;
        }
        return s.scenario().tools().stream().anyMatch(tool -> name.equals(tool.name()));
    }

    @PreDestroy
    void shutdown() {
        toolExecutor.shutdownNow();
    }
}
