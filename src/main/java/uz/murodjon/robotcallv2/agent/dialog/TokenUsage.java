package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

import uz.murodjon.robotcallv2.agent.metrics.LlmMetrics;

/**
 * Token accounting for one LLM turn (PROJECT.md §2.6).
 *
 * <p>A turn that calls a tool makes two HTTP calls, so the calls are summed. Within one
 * streamed call a provider may report usage on every chunk, and cumulatively: Gemini sends
 * the same {@code promptTokenCount} on each chunk and a {@code candidatesTokenCount} that
 * grows to the final figure. A snapshot with the previous one's prompt and no less
 * completion is therefore the same call moving on, and only its growth is counted — summing
 * each chunk counted the prompt once per chunk and the completion as a running total of
 * running totals, and that is what the call was billed.
 *
 * <p>Gemini reports thinking separately from the answer: {@code candidatesTokenCount}
 * (what Spring AI surfaces as completion tokens) excludes {@code thoughtsTokenCount},
 * and both are billed at the output rate. Counting only completion tokens would
 * under-report a turn — visibly so, since {@code totalTokenCount} is then larger than
 * prompt + completion — and would let a call slip past {@code maxTokensPerCall}. So
 * thoughts are folded into the completion figure here.
 *
 * <p>Public for the post-call summary ({@code SummaryService}), which is billed to the call
 * from the same counts.
 */
public class TokenUsage {

    private static final Logger log = LoggerFactory.getLogger(TokenUsage.class);

    private String model;
    private long prompt;
    private long completion;
    private long cached;
    private long thinking;
    private long lastPrompt = -1;
    private long lastCompletion = -1;
    private long lastCached;
    private long lastThinking;

    public void add(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        // Which model answered — billed at that model's price. The first named one wins: the
        // calls one TokenUsage sums (a turn and its tool follow-up) go to the same model.
        if (model == null) {
            String reported = response.getMetadata().getModel();
            if (reported != null && !reported.isBlank()) {
                model = reported;
            }
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
        long cachedTokens = cachedTokens(usage);
        long thinkingTokens = thinkingTokens(usage);
        boolean sameCall = promptTokens == lastPrompt && completionTokens >= lastCompletion;
        if (sameCall) {
            // The same call, further on: only what it added since the last chunk.
            completion += completionTokens - lastCompletion;
            cached += Math.max(0, cachedTokens - lastCached);
            thinking += Math.max(0, thinkingTokens - lastThinking);
        } else {
            prompt += promptTokens;
            completion += completionTokens;
            cached += cachedTokens;
            thinking += thinkingTokens;
        }
        lastPrompt = promptTokens;
        lastCompletion = completionTokens;
        lastCached = sameCall ? Math.max(lastCached, cachedTokens) : cachedTokens;
        lastThinking = sameCall ? Math.max(lastThinking, thinkingTokens) : thinkingTokens;
    }

    /** Everything this turn was billed for — what the per-call budget counts (§C14). */
    long total() {
        return prompt + completion;
    }

    /** The model the provider answered with, or null when no response named one. */
    public String model() {
        return model;
    }

    /** Prompt tokens, cached ones included. */
    public long promptTokens() {
        return prompt;
    }

    /** Tokens the model wrote, thinking included. */
    public long completionTokens() {
        return completion;
    }

    /** Of {@link #promptTokens()}, how many the provider served at the cached rate. */
    public long cachedTokens() {
        return cached;
    }

    /** Of {@link #completionTokens()}, how many were the model's thinking. */
    public long thinkingTokens() {
        return thinking;
    }

    void publish(DialogSession s, LlmMetrics metrics) {
        if (prompt == 0 && completion == 0) {
            return;
        }
        metrics.recordLlmUsage(prompt, completion, cached);
        s.addTokenBreakdown(model, prompt, completion, cached);
        // Info, not debug: next to "turn latency" it says what a slow first token was spent on —
        // thinking before the first word, or a prompt the cache did not cover.
        log.info("[{}] tokens: prompt={} (cached {}, {}%), completion={} (thinking {})",
                s.channelId(), prompt, cached, prompt > 0 ? cached * 100 / prompt : 0, completion, thinking);
    }

    /**
     * The share of the prompt billed at the cached rate. Gemini reports it on its own usage type;
     * OpenAI's {@code prompt_tokens_details.cached_tokens} arrives as the generic cache-read count —
     * reading only Gemini's showed every GPT turn as "cached 0" and billed its cached prefix at the
     * full rate. Capped at the prompt: a vendor that counts cache reads outside the prompt
     * (Anthropic) must not make the cached share exceed the whole.
     */
    static long cachedTokens(Usage usage) {
        if (usage instanceof GoogleGenAiUsage google) {
            return value(google.getCachedContentTokenCount());
        }
        Long cacheRead = usage.getCacheReadInputTokens();
        return cacheRead == null ? 0 : Math.max(0, Math.min(cacheRead, value(usage.getPromptTokens())));
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
