package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Cascade-pipeline LLM metrics: errors and retries, token accounting, speculative replies
 * and turn latency. Realtime engines report through {@link RealtimeMetrics} instead.
 * Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class LlmMetrics {

    private final MeterRegistry registry;
    private final Counter llmErrors;
    private final Counter speculationsStarted;
    private final Counter speculationsHit;
    private final Counter speculationsMissed;
    private final Counter llmPromptTokens;
    private final Counter llmCompletionTokens;
    private final Counter llmCachedTokens;
    private final Counter spokenLineRetries;
    private final Counter llmRetries;
    private final Timer llmTurn;

    public LlmMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.llmErrors = Counter.builder("voice.llm.errors").register(registry);
        // Replies started before the caller had finished speaking. The ratio hit/started
        // is the whole business case: a miss buys nothing and is billed anyway, so
        // preemptive generation is only worth having while most guesses land. The missed
        // tokens are NOT in the counters below — the stream is cancelled long before the
        // provider reports usage — so read a miss as roughly one prompt's worth.
        this.speculationsStarted = Counter.builder("voice.llm.speculation.started")
                .description("replies begun on an interim transcript")
                .register(registry);
        this.speculationsHit = Counter.builder("voice.llm.speculation.hit")
                .description("speculative replies the final transcript confirmed")
                .register(registry);
        this.speculationsMissed = Counter.builder("voice.llm.speculation.miss")
                .description("speculative replies thrown away because the caller said something else")
                .register(registry);
        // Token accounting for the LLM (§2.6). cached = the share of the prompt the
        // provider served from its context cache, i.e. the payoff of keeping the
        // request prefix stable; if this stays at zero, prefix caching is not working.
        this.llmPromptTokens = Counter.builder("voice.llm.tokens.prompt").register(registry);
        this.llmCompletionTokens = Counter.builder("voice.llm.tokens.completion").register(registry);
        this.llmCachedTokens = Counter.builder("voice.llm.tokens.cached")
                .description("prompt tokens the provider served from its context cache")
                .register(registry);
        // Each one is a second LLM round trip inside a live turn (DialogEngine.streamTurn),
        // so this is the direct read-out on whether the prompt is getting the model to
        // speak and call a tool in the same breath. Watch it against voice.turnaround.latency.
        this.spokenLineRetries = Counter.builder("voice.llm.spoken.line.retries")
                .description("turns the model answered with tool calls only, forcing a second request")
                .register(registry);
        // Requests that failed outright and were sent again. Against voice.llm.errors this
        // says how much of the provider's flakiness the caller never heard: a retry that
        // lands costs a few hundred milliseconds, a turn lost to a 503 costs the answer.
        this.llmRetries = Counter.builder("voice.llm.retries")
                .description("turns whose request failed transiently and was attempted once more")
                .register(registry);
        this.llmTurn = Timer.builder("voice.llm.turn.latency").publishPercentileHistogram().register(registry);
    }

    public void llmError() {
        llmErrors.increment();
    }

    /** A turn produced tool calls but no speakable text, so it had to be asked again. */
    public void spokenLineRetry() {
        spokenLineRetries.increment();
    }

    /** A turn's request failed transiently and was sent once more. */
    public void llmRetry() {
        llmRetries.increment();
    }

    /**
     * Record one LLM call's token usage. {@code cached} is the part of {@code prompt}
     * the provider served from its context cache — it is included in {@code prompt},
     * not additional to it.
     */
    public void recordLlmUsage(long prompt, long completion, long cached) {
        if (prompt > 0) {
            llmPromptTokens.increment(prompt);
        }
        if (completion > 0) {
            llmCompletionTokens.increment(completion);
        }
        if (cached > 0) {
            llmCachedTokens.increment(cached);
        }
    }

    /** A reply was begun on an interim transcript, before the caller had finished. */
    public void speculationStarted() {
        speculationsStarted.increment();
    }

    /** The final transcript said what the interim did, so the reply was already written. */
    public void speculationHit() {
        speculationsHit.increment();
    }

    /** The caller said something else, so the speculative reply was thrown away. */
    public void speculationMiss() {
        speculationsMissed.increment();
    }

    /**
     * Running totals behind the efficiency ratios — speculations begun and confirmed,
     * prompt tokens sent and served from the provider's cache. Read as deltas between two
     * checks: each one is the numerator or the denominator of a ratio that says whether an
     * optimization is paying for itself.
     */
    public long speculationStartedCount() {
        return (long) speculationsStarted.count();
    }

    public long speculationHitCount() {
        return (long) speculationsHit.count();
    }

    public long llmPromptTokenCount() {
        return (long) llmPromptTokens.count();
    }

    public long llmCachedTokenCount() {
        return (long) llmCachedTokens.count();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    /** @return elapsed nanoseconds, so callers can also accumulate this per-call (§10.5 "Texnik" tab). */
    public long stopLlmTurn(Timer.Sample sample) {
        return sample.stop(llmTurn);
    }
}
