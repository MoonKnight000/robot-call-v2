package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.aimodel.domain.EffectiveAiModelConfig;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.StageDef;
import uz.murodjon.uysotvoice.scenario.dto.ToolDef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tools a turn may call, and what happens when the model calls them (PROJECT.md
 * §4.2, ROADMAP A.3). Two questions, one class: which declarations go out with the
 * request, and how the calls that come back are executed.
 *
 * <p>They belong together because the second depends on the first — a call to a tool
 * this stage did not offer is answered with an error rather than executed, and that is
 * only decidable against the list that was sent.
 *
 * <p>Where the tools themselves come from: {@link DialogTools} holds the hardcoded ones
 * (they carry code-level guardrails, e.g. rejecting a promised date in the past) and
 * {@link ScenarioToolCallbackFactory} builds every tool a scenario declares for itself.
 */
@Component
public class TurnTools {

    private static final Logger log = LoggerFactory.getLogger(TurnTools.class);

    /**
     * Tools every stage needs regardless of scenario (ROADMAP A.1/A.3 — the fixed
     * universal set every {@code ScenarioDefinition} gets on top of its own declared
     * tools): the FSM has to be able to move, escalate, flag the wrong person, honour
     * an opt-out, and hang up from anywhere in the call.
     */
    private static final Set<String> ALWAYS_AVAILABLE_TOOLS =
            Set.of("transitionTo", "requestHumanTransfer", "recordWrongPerson", "recordDoNotCall", "endCall");

    private final DialogProperties props;

    public TurnTools(DialogProperties props) {
        this.props = props;
    }

    /**
     * The tools this stage may use, built from the bound scenario (ROADMAP A.3). Every
     * tool declaration is re-sent (and re-billed) on every turn, and the ones that
     * cannot legitimately fire yet are also the ones a model is most likely to misfire.
     *
     * <p>Two of a scenario's tools — {@code recordPaymentPromise}/{@code
     * recordRefusalReason} — are hardcoded {@link DialogTools} methods and only appear
     * when the scenario actually declares a matching {@link ToolDef} name; every other
     * declared tool is built generically by {@link ScenarioToolCallbackFactory}.
     *
     * <p>The set only changes on a stage transition, so the request prefix — which
     * includes the tool declarations — still holds still for several turns at a time.
     * Set {@code voice-agent.dialog.state-scoped-tools=false} to send them all.
     */
    public List<ToolCallback> build(DialogSession s) {
        Set<String> declared = declaredToolNames(s.scenario());
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallback fixed : MethodToolCallbackProvider.builder()
                .toolObjects(new DialogTools(s)).build().getToolCallbacks()) {
            String name = fixed.getToolDefinition().name();
            // requestHumanTransfer/recordWrongPerson/recordDoNotCall/endCall/transitionTo
            // are universal and always included; recordPaymentPromise/recordRefusalReason
            // only when this scenario actually declares them.
            if (ALWAYS_AVAILABLE_TOOLS.contains(name) || declared.contains(name)) {
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
        if (!props.stateScopedTools()) {
            return callbacks;
        }
        Set<String> allowed = allowedTools(s);
        return callbacks.stream()
                .filter(callback -> allowed.contains(callback.getToolDefinition().name()))
                .toList();
    }

    private static Set<String> declaredToolNames(ScenarioDefinition def) {
        if (def.tools() == null) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        def.tools().forEach(t -> names.add(t.name()));
        return names;
    }

    /**
     * Tools callable from the session's current stage: the fixed universal set plus
     * whatever the current {@link StageDef#allowedTools()} names — or, when a stage
     * declares no restriction (the common case), every tool this scenario has.
     */
    private static Set<String> allowedTools(DialogSession s) {
        StageDef stage = SystemPromptFactory.stageOf(s.scenario(), s.state());
        Set<String> allowed = new HashSet<>(ALWAYS_AVAILABLE_TOOLS);
        // null = not specified, every scenario tool is available here (the common case);
        // an explicit (possibly empty) list means exactly those tools and no others —
        // debt-collection's GREETING/CLOSING/etc. stages rely on the empty-list case to
        // exclude recordPaymentPromise/recordRefusalReason.
        List<String> perStage = stage != null ? stage.allowedTools() : null;
        if (perStage == null) {
            if (s.scenario().tools() != null) {
                s.scenario().tools().forEach(t -> allowed.add(t.name()));
            }
        } else {
            allowed.addAll(perStage);
        }
        return allowed;
    }

    /**
     * This turn's request options: the tools, the call's company AI-model overrides, and
     * Spring AI's built-in tool-calling loop switched off — {@link #run} executes the
     * calls here instead.
     *
     * <p>That loop answers a tool call by replaying the model's own {@code functionCall}
     * back to it alongside the result, and Gemini 3 rejects the replay unless every
     * function call carries the opaque {@code thought_signature} it was issued with
     * (HTTP 400, "Function call is missing a thought_signature in functionCall parts").
     * Getting those signatures out of the provider means echoing the model's thinking
     * back verbatim — which this engine cannot do, because the same parsing path would
     * hand that reasoning to the TTS and speak it to the caller.
     *
     * <p>Running the tools ourselves removes the round trip that needs a signature at
     * all. Nothing is lost: the tools move the FSM and record outcomes, their return
     * strings are confirmations, and the conversation history was already text-only. It
     * is also one LLM call cheaper per tool-calling turn, which the &lt;1s budget (§1.3)
     * notices.
     *
     * <p>A fresh options object every turn — the ChatClient merges the tool callbacks
     * into the instance it is handed, so a shared one would accumulate them.
     *
     * <p>{@code GoogleGenAiChatOptions} implements {@code ToolCallingChatOptions}, so one
     * object carries both the tools and the §11 model settings ({@link
     * DialogSession#aiModel()}); a null override leaves the Spring AI auto-configured
     * default in place, the same runtime-merge-over-default Spring AI already does for
     * every unset field.
     */
    public ChatOptions buildOptions(DialogSession s, List<ToolCallback> tools) {
        EffectiveAiModelConfig aiModel = s.aiModel();
        return GoogleGenAiChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .model(aiModel.model())
                .temperature(aiModel.temperature())
                .maxOutputTokens(aiModel.maxOutputTokens())
                .build();
    }

    /** Every tool call carried by one response (or one streamed chunk of it). */
    public List<AssistantMessage.ToolCall> extractCalls(ChatResponse response) {
        if (response == null) {
            return List.of();
        }
        return response.getResults().stream()
                .map(Generation::getOutput)
                .flatMap(output -> output.getToolCalls().stream())
                .toList();
    }

    /**
     * Run the tools the model asked for, in the order it asked for them.
     *
     * <p>A failure is logged and reported rather than thrown: a tool that did not run
     * is a lost outcome, but an exception here would cost the caller the whole reply.
     *
     * @return what the tools returned, for the model to see — or null if it called none
     */
    public String run(DialogSession s, List<ToolCallback> tools, List<AssistantMessage.ToolCall> calls) {
        if (calls.isEmpty()) {
            return null;
        }
        List<String> results = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            ToolCallback callback = tools.stream()
                    .filter(t -> t.getToolDefinition().name().equals(call.name()))
                    .findFirst()
                    .orElse(null);
            if (callback == null) {
                // A tool this state does not offer (see build) — the model invented the
                // name, or is reaching for an outcome that is not on the table yet.
                log.warn("[{}] model called unavailable tool {} in {}",
                        s.channelId(), call.name(), s.state());
                results.add("XATO: " + call.name() + " hozirgi bosqichda mavjud emas");
                continue;
            }
            try {
                String result = callback.call(call.arguments());
                log.debug("[{}] tool {}({}) -> {}", s.channelId(), call.name(), call.arguments(), result);
                if (!result.isBlank()) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("[{}] tool {} failed: {}", s.channelId(), call.name(), e.getMessage());
                results.add("XATO: " + call.name() + " bajarilmadi");
            }
        }
        return results.isEmpty() ? null : String.join(" ", results);
    }
}
