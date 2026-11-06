package uz.murodjon.robotcallv2.agent.provider;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import uz.murodjon.robotcallv2.shared.dialog.ReasoningEffort;

import java.util.ArrayList;
import java.util.List;

/** Builds {@link PortableChatOptions}; reached through {@link PortableChatOptions#builder()}. */
public class PortableChatOptionsBuilder extends DefaultToolCallingChatOptions.Builder<PortableChatOptionsBuilder> {

    private ReasoningEffort reasoningEffort;
    private List<String> allowedToolNames;

    PortableChatOptionsBuilder() {
    }

    /** @param reasoningEffort the thinking level, or {@code null} for the vendor's configured default */
    public PortableChatOptionsBuilder reasoningEffort(ReasoningEffort reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
        return this;
    }

    /** @param allowedToolNames the declared tools the model may call; {@code null} leaves it free */
    public PortableChatOptionsBuilder allowedToolNames(List<String> allowedToolNames) {
        this.allowedToolNames = allowedToolNames != null ? new ArrayList<>(allowedToolNames) : null;
        return this;
    }

    @Override
    public PortableChatOptionsBuilder clone() {
        PortableChatOptionsBuilder copy = super.clone();
        copy.allowedToolNames = allowedToolNames != null ? new ArrayList<>(allowedToolNames) : null;
        return copy;
    }

    /**
     * The common merge, plus this type's own fields. A vendor's builder (a caller that still
     * builds Gemini or OpenAI options — summaries, judges) keeps its thinking level by way of
     * {@link ReasoningEffort}, and OpenAI's {@code max_completion_tokens} by way of the token cap,
     * since the routing model's options are of this type.
     */
    @Override
    public PortableChatOptionsBuilder combineWith(ChatOptions.Builder<?> other) {
        super.combineWith(other);
        if (other instanceof PortableChatOptionsBuilder that) {
            if (that.reasoningEffort != null) {
                this.reasoningEffort = that.reasoningEffort;
            }
            if (that.allowedToolNames != null) {
                this.allowedToolNames = new ArrayList<>(that.allowedToolNames);
            }
        } else if (other instanceof GoogleGenAiChatOptions.Builder google) {
            GoogleGenAiChatOptions options = google.clone().build();
            if (options.getThinkingLevel() != null) {
                ReasoningEffort effort = ReasoningEffort.parse(options.getThinkingLevel().name());
                if (effort != null) {
                    this.reasoningEffort = effort;
                }
            }
        } else if (other instanceof OpenAiChatOptions.Builder openAi) {
            OpenAiChatOptions options = openAi.clone().build();
            ReasoningEffort effort = ReasoningEffort.parse(options.getReasoningEffort());
            if (effort != null) {
                this.reasoningEffort = effort;
            }
            if (options.getMaxCompletionTokens() != null) {
                this.maxTokens = options.getMaxCompletionTokens();
            }
        }
        return this;
    }

    @Override
    public PortableChatOptions build() {
        return new PortableChatOptions(toolCallbacks, toolContext, model, frequencyPenalty, maxTokens,
                presencePenalty, stopSequences, temperature, topK, topP, reasoningEffort, allowedToolNames);
    }
}
