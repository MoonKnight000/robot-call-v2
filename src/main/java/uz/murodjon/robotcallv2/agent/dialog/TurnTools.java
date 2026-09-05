package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;

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
    private final ObjectProvider<ChatModel> chatModelProvider;

    public TurnTools(DialogProperties props, ObjectProvider<ChatModel> chatModelProvider) {
        this.props = props;
        this.chatModelProvider = chatModelProvider;
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
     * strings are confirmations, and the conversation history was already text-only.
     *
     * <p>A fresh options builder every turn — the ChatClient merges the tool callbacks
     * into the instance it is handed, so a shared one would accumulate them.
     */
    public ChatOptions buildOptions(DialogSession s, List<ToolCallback> tools) {
        return buildOptions(s, tools, null);
    }

    /**
     * The same, on a different model for this one turn.
     *
     * @param model the model to use instead of the company's configured one, or
     *              {@code null} to keep it. A one-word acknowledgement does not need the
     *              model an objection does, and the difference is paid in time-to-first
     *              -token on every turn ({@code DialogProperties#fastModel})
     */
    public ChatOptions buildOptions(DialogSession s, List<ToolCallback> tools, String model) {
        EffectiveAiModelConfig aiModel = s.aiModel();
        String selectedModel = model != null && !model.isBlank() ? model : aiModel.model();
        ChatModel chatModel = chatModelProvider != null ? chatModelProvider.getIfAvailable() : null;

        boolean isOpenAi = (chatModel instanceof OpenAiChatModel)
                || (selectedModel != null && (selectedModel.startsWith("llama") || selectedModel.startsWith("mixtral")
                || selectedModel.startsWith("gpt-") || selectedModel.startsWith("qwen")));

        if (isOpenAi) {
            var builder = OpenAiChatOptions.builder()
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .model(selectedModel);
            if (aiModel.maxOutputTokens() != null) {
                builder.maxTokens(aiModel.maxOutputTokens());
            }
            if (aiModel.temperature() != null) {
                builder.temperature(aiModel.temperature());
            }
            return builder.build();
        }

        var builder = GoogleGenAiChatOptions.builder()
                .toolCallbacks(tools)
                // Declarations only (see above). Left at Spring AI's default of true, a
                // real call ran every tool twice — once in the framework's loop, once in
                // run() — and paid an LLM round trip for each: 35 tool executions and an
                // 11s reply on a seven-line call. Worse, a speculative request
                // (TurnRunner#startSpeculation) carries these same live callbacks, so the
                // framework was moving the FSM and recording payment promises from a guess
                // at what the caller was still in the middle of saying.
                .internalToolExecutionEnabled(false)
                .model(selectedModel)
                .maxOutputTokens(aiModel.maxOutputTokens())
                .thinkingLevel(GoogleGenAiThinkingLevel.LOW);

        // Gemini 3.8 Flash (and Gemini 3+) deprecates sampling parameters (temperature, top_p, top_k).
        // Must strip temperature from generation configs for Gemini 3+.
        if (!isGemini3OrLater(selectedModel) && aiModel.temperature() != null) {
            builder.temperature(aiModel.temperature());
        }
        return builder.build();
    }

    private static boolean isGemini3OrLater(String model) {
        if (model == null || model.isBlank()) {
            return true; // default model is gemini-3.8-flash
        }
        return model.toLowerCase().contains("gemini-3");
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
                // A tool this stage does not offer (see build) — the model invented the
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
