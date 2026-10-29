package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmCatalogUseCase;
import uz.murodjon.robotcallv2.agent.metrics.RealtimeMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProperties;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeSession;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.CatalogSearchUseCase;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** Appended to a tool answer the engine gets after it already spoke this turn — see {@link #runTool}. */
    private static final String ALREADY_SPOKEN = " [SYSTEM: the client has already heard what you said this turn — "
            + "do not say it again. If you have nothing new to say, stay silent and wait for the client's answer.]";

    private final KnowledgeRetrievalUseCase knowledgeRetrieval;
    private final CatalogSearchUseCase catalogSearch;
    private final RealtimeProperties realtimeProperties;
    private final RealtimeMetrics metrics;
    private final StageScriptRunner stageScriptRunner;
    private final CrmCatalogUseCase crmCatalog;

    /**
     * Tool bodies hit the database and the CRM, and they arrive on the engine's network
     * thread — the same thread its next audio frame comes in on. Virtual threads because
     * the work is all waiting, and there is one per tool call, not one per call.
     */
    private final ExecutorService toolExecutor = Executors.newVirtualThreadPerTaskExecutor();

    RealtimeToolRunner(KnowledgeRetrievalUseCase knowledgeRetrieval, CatalogSearchUseCase catalogSearch,
                       RealtimeProperties realtimeProperties, RealtimeMetrics metrics,
                       StageScriptRunner stageScriptRunner, CrmCatalogUseCase crmCatalog) {
        this.knowledgeRetrieval = knowledgeRetrieval;
        this.catalogSearch = catalogSearch;
        this.realtimeProperties = realtimeProperties;
        this.metrics = metrics;
        this.stageScriptRunner = stageScriptRunner;
        this.crmCatalog = crmCatalog;
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
        // pays for the description in every request, and may call it out of curiosity.
        if (s.agent() != null && s.agent().useRag()) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealtimeKnowledgeTools(s, knowledgeRetrieval))
                    .build().getToolCallbacks()));
        }
        // The catalog search, for an agent that has a catalog (UNIVERSAL-OPERATOR-PLAN B.4).
        // Not tied to useRag: an order cannot be placed without the SKU it finds.
        Long agentId = s.agent() != null ? s.agent().id() : null;
        if (catalogSearch.existsByCompanyIdAndAgentId(s.companyId(), agentId)) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new CatalogTools(s, s.companyId(), agentId, catalogSearch))
                    .build().getToolCallbacks()));
        }
        // The developer's Uysot lookups, for a company connected to Uysot (RealEstateTools).
        if (crmCatalog != null && crmCatalog.isUysotConnected(s.companyId())) {
            callbacks.addAll(List.of(MethodToolCallbackProvider.builder()
                    .toolObjects(new RealEstateTools(s, s.companyId(), crmCatalog))
                    .build().getToolCallbacks()));
        }
        for (ToolCallback fixed : MethodToolCallbackProvider.builder()
                .toolObjects(new DialogTools(s, stageScriptRunner)).build().getToolCallbacks()) {
            String name = fixed.getToolDefinition().name();
            if ("sendMidCallSms".equals(name)) {
                if (s.agent() != null && s.agent().callBehaviour().midCallSmsEnabled()) {
                    callbacks.add(fixed);
                }
                continue;
            }
            if (DialogTools.CASCADE_ONLY_TOOL_NAMES.contains(name)) {
                continue;
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
        return callbacks;
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
        boolean spokeThisTurn = session.replyOpen();
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
            log.info("[{}] tool {} -> {}", s.channelId(), name, result);
            return result;
        } catch (Exception e) {
            log.warn("[{}] tool {} failed: {}", s.channelId(), name, e.getMessage());
            return "ERROR: " + e.getMessage();
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
        if ("recordPaymentPromise".equals(name) && s.stageJustEntered()) {
            return "ERROR: you have only just entered this stage — the client has not named a date yet. "
                    + "First say this stage's line and ask the client for the exact day.";
        }
        if (DialogTools.ALWAYS_AVAILABLE_TOOL_NAMES.contains(name)) {
            return null;
        }
        StageDef current = s.scenario() == null ? null : s.scenario().findStage(s.state());
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
        List<String> allowed = stage == null ? null : stage.allowedTools();
        if (allowed == null || allowed.contains(name)) {
            return null;
        }
        return "ERROR: " + name + " cannot be called in stage " + s.state()
                + (allowed.isEmpty() ? "" : " — only " + String.join(", ", allowed) + " can be called here")
                + ".";
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
