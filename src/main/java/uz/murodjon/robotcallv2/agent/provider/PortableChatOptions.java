package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A turn's request options in no vendor's shape: the common fields plus the settings every
 * vendor has in some form ({@link #getReasoningEffort}, {@link #getAllowedToolNames}).
 * {@link RoutingChatModel} turns them into the chosen vendor's options through that vendor's
 * {@link VendorChatOptionsStrategy}, once the model id has told it which vendor that is.
 *
 * <p>Why not build the vendor's options up front: a ChatClient merges the request into
 * {@code chatModel.getOptions()} with {@code combineWith}, which keeps a vendor's own fields
 * only when both sides are that vendor's type. The routing model is no single vendor, so
 * OpenAI's {@code reasoning_effort} and {@code max_completion_tokens} were dropped there and a
 * gpt-6 turn went out with {@code max_tokens} again. This type survives that merge whole.
 */
public class PortableChatOptions extends DefaultToolCallingChatOptions {

    private final ReasoningEffort reasoningEffort;
    private final List<String> allowedToolNames;

    PortableChatOptions(List<ToolCallback> toolCallbacks, Map<String, Object> toolContext, String model,
                        Double frequencyPenalty, Integer maxTokens, Double presencePenalty,
                        List<String> stopSequences, Double temperature, Integer topK, Double topP,
                        ReasoningEffort reasoningEffort, List<String> allowedToolNames) {
        super(toolCallbacks, toolContext, model, frequencyPenalty, maxTokens, presencePenalty, stopSequences,
                temperature, topK, topP);
        this.reasoningEffort = reasoningEffort;
        this.allowedToolNames = allowedToolNames != null ? List.copyOf(allowedToolNames) : null;
    }

    public static PortableChatOptionsBuilder builder() {
        return new PortableChatOptionsBuilder();
    }

    /** @return the agent's thinking level, or {@code null} for the vendor's configured default */
    public ReasoningEffort getReasoningEffort() {
        return reasoningEffort;
    }

    /**
     * @return the declared tools the model may call on this turn — {@code null} leaves it free,
     *         an empty list allows none
     */
    public List<String> getAllowedToolNames() {
        return allowedToolNames;
    }

    /** A builder of the same type, so a ChatClient's merge keeps every field. */
    @Override
    public PortableChatOptionsBuilder mutate() {
        return builder()
                .model(getModel())
                .frequencyPenalty(getFrequencyPenalty())
                .maxTokens(getMaxTokens())
                .presencePenalty(getPresencePenalty())
                .stopSequences(getStopSequences())
                .temperature(getTemperature())
                .topK(getTopK())
                .topP(getTopP())
                .toolCallbacks(getToolCallbacks())
                .toolContext(getToolContext())
                .reasoningEffort(reasoningEffort)
                .allowedToolNames(allowedToolNames);
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }
        PortableChatOptions that = (PortableChatOptions) o;
        return reasoningEffort == that.reasoningEffort && Objects.equals(allowedToolNames, that.allowedToolNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reasoningEffort, allowedToolNames);
    }
}
