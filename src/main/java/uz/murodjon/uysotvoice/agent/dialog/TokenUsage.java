package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;

/**
 * Token accounting for one LLM turn (PROJECT.md §2.6).
 *
 * <p>Providers report usage once per HTTP call — on the final streamed chunk — and a
 * turn that calls a tool makes two calls, so the snapshots are summed. Consecutive
 * identical snapshots are ignored: a provider that repeats cumulative usage on every
 * chunk would otherwise be counted once per chunk.
 *
 * <p>Gemini reports thinking separately from the answer: {@code candidatesTokenCount}
 * (what Spring AI surfaces as completion tokens) excludes {@code thoughtsTokenCount},
 * and both are billed at the output rate. Counting only completion tokens would
 * under-report a turn — visibly so, since {@code totalTokenCount} is then larger than
 * prompt + completion — and would let a call slip past {@code maxTokensPerCall}. So
 * thoughts are folded into the completion figure here.
 */
class TokenUsage {

    private static final Logger log = LoggerFactory.getLogger(TokenUsage.class);

    private long prompt;
    private long completion;
    private long cached;
    private long lastPrompt = -1;
    private long lastCompletion = -1;

    void add(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        long promptTokens = value(usage.getPromptTokens());
        long completionTokens = value(usage.getCompletionTokens()) + thinkingTokens(usage);
        if (promptTokens == 0 && completionTokens == 0) {
            return; // an ordinary content chunk carries no usage
        }
        if (promptTokens == lastPrompt && completionTokens == lastCompletion) {
            return;
        }
        lastPrompt = promptTokens;
        lastCompletion = completionTokens;
        prompt += promptTokens;
        completion += completionTokens;
        cached += cachedTokens(usage);
    }

    /** Everything this turn was billed for — what the per-call budget counts (§C14). */
    long total() {
        return prompt + completion;
    }

    void publish(DialogSession s, VoiceMetrics metrics) {
        if (prompt == 0 && completion == 0) {
            return;
        }
        metrics.recordLlmUsage(prompt, completion, cached);
        s.addTokenBreakdown(prompt, completion, cached);
        log.debug("[{}] tokens: prompt={} (cached {}), completion={}",
                s.channelId(), prompt, cached, completion);
    }

    /** The share of the prompt Gemini billed at the cached rate. */
    private static long cachedTokens(Usage usage) {
        if (usage instanceof GoogleGenAiUsage google) {
            return value(google.getCachedContentTokenCount());
        }
        return 0;
    }

    /**
     * Tokens Gemini spent thinking before answering. Billed as output, and not part of
     * the completion count — see the class javadoc. Stays 0 while the dialog runs at
     * thinking-level MINIMAL, which is the point of that setting.
     */
    private static long thinkingTokens(Usage usage) {
        if (usage instanceof GoogleGenAiUsage google) {
            return value(google.getThoughtsTokenCount());
        }
        return 0;
    }

    private static long value(Integer count) {
        return count == null ? 0 : count;
    }
}
