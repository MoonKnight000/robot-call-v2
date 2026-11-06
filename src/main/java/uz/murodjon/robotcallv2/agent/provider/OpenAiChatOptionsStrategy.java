package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.Locale;

/**
 * OpenAI and the OpenAI-compatible ids it serves. The reasoning families (gpt-5*, gpt-6*, o1/o3/o4…)
 * answer 400 to {@code max_tokens}, to a temperature other than 1 and to either penalty, so they get
 * {@code max_completion_tokens} and {@code reasoning_effort} only — without the effort they think at
 * "medium": 4.5–6.4 s to the first token on gpt-6-luna against 1.0–1.4 s at "low". The other models
 * keep the classic fields and never see {@code reasoning_effort}.
 */
@Component
public class OpenAiChatOptionsStrategy extends AbstractVendorChatOptionsStrategy<OpenAiChatOptions.Builder> {

    /** The deployment's effort (spring.ai.openai.chat.reasoning-effort) for an agent that sets none. */
    private final ReasoningEffort defaultEffort;

    public OpenAiChatOptionsStrategy(@Value("${spring.ai.openai.chat.reasoning-effort:low}") String defaultEffort) {
        this.defaultEffort = ReasoningEffort.parse(defaultEffort);
    }

    @Override
    public ChatVendor vendor() {
        return ChatVendor.OPENAI;
    }

    @Override
    protected OpenAiChatOptions.Builder newBuilder() {
        return OpenAiChatOptions.builder();
    }

    @Override
    protected void applyTuning(OpenAiChatOptions.Builder builder, PortableChatOptions request, String model) {
        OpenAiChatOptions current = builder.clone().build();
        if (isReasoningModel(model)) {
            Integer cap = request.getMaxTokens() != null ? request.getMaxTokens()
                    : current.getMaxCompletionTokens() != null ? current.getMaxCompletionTokens() : current.getMaxTokens();
            setTokenCap(builder, null, cap);
            builder.temperature(null).frequencyPenalty(null).presencePenalty(null);
            ReasoningEffort effort = request.getReasoningEffort() != null ? request.getReasoningEffort() : defaultEffort;
            if (refusesEffortWithTools(model) && request.getToolCallbacks() != null && !request.getToolCallbacks().isEmpty()) {
                // gpt-6 on /v1/chat/completions: "Function tools with reasoning_effort are not supported …
                // set reasoning_effort to 'none'" — every turn of eval #19 failed on it. Unset is no way out
                // either: the vendor default is a reasoning level too.
                builder.reasoningEffort("none");
                return;
            }
            builder.reasoningEffort(effort != null ? findReasoningEffort(effort, model) : null);
            return;
        }
        if (request.getMaxTokens() != null) {
            setTokenCap(builder, request.getMaxTokens(), null);
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(request.getFrequencyPenalty());
        }
        if (request.getPresencePenalty() != null) {
            builder.presencePenalty(request.getPresencePenalty());
        }
        builder.reasoningEffort(null);
    }

    /**
     * The value OpenAI accepts for this model. The lightest level differs by family: gpt-5.x takes
     * "none" (measured on gpt-5.6-luna), while gpt-6 answers 400 to "minimal" — so every model not
     * known to take a lighter one falls back to "low".
     */
    static String findReasoningEffort(ReasoningEffort effort, String model) {
        return switch (effort) {
            case NONE, MINIMAL -> findLightestEffort(model);
            case LOW, MEDIUM, HIGH -> effort.name().toLowerCase(Locale.ROOT);
        };
    }

    /** gpt-5*, gpt-6* and the o-series spend reasoning tokens out of the same cap. */
    public static boolean isReasoningModel(String model) {
        if (model == null) {
            return false;
        }
        String id = model.toLowerCase(Locale.ROOT);
        return id.startsWith("gpt-5") || id.startsWith("gpt-6") || id.matches("o\\d.*");
    }

    /**
     * gpt-6 takes function tools on Chat Completions only without reasoning (2026-11-06: 400 at "low",
     * 200 at "none"); thinking with tools needs the Responses API, which Spring AI's chat model does not use.
     * gpt-5.6 answers the same 400 (eval #30, gpt-5.6-luna as the fast model).
     */
    static boolean refusesEffortWithTools(String model) {
        if (model == null) {
            return false;
        }
        String id = model.toLowerCase(Locale.ROOT);
        return id.startsWith("gpt-6") || id.startsWith("gpt-5.6");
    }

    private static String findLightestEffort(String model) {
        String id = model == null ? "" : model.toLowerCase(Locale.ROOT);
        return id.startsWith("gpt-5.") ? "none" : "low";
    }

    /**
     * OpenAI's builder refuses to hold both caps and silently keeps the old one when the other is set,
     * so both are cleared before the one this model reads is written.
     */
    private static void setTokenCap(OpenAiChatOptions.Builder builder, Integer maxTokens, Integer maxCompletionTokens) {
        builder.maxCompletionTokens(null);
        builder.maxTokens(null);
        if (maxCompletionTokens != null) {
            builder.maxCompletionTokens(maxCompletionTokens);
        } else if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
    }
}
