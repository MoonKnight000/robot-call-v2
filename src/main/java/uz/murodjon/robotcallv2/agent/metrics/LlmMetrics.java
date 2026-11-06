package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
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
    private final Counter turnCuesComplete;
    private final Counter turnCuesIncomplete;
    private final Counter waitForCallerTimeouts;
    private final Counter llmPromptTokens;
    private final Counter llmCompletionTokens;
    private final Counter llmCachedTokens;
    private final Counter spokenLineRetries;
    private final Counter llmRetries;
    private final DistributionSummary systemPromptChars;
    private final DistributionSummary turnPromptTokens;
    private final Counter promptsOverBudget;
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
        // The speculative reply's first chunk read as an end-of-turn verdict (SpeechGate#setLlmTurnCue).
        // Read against voice.dialog.turn.stitched: "complete" verdicts that still end in a stitched
        // turn are callers the shorter wait cut off.
        this.turnCuesComplete = Counter.builder("voice.llm.speculation.turn.cue")
                .description("end-of-turn verdicts taken from the speculative reply")
                .tag("verdict", "complete")
                .register(registry);
        this.turnCuesIncomplete = Counter.builder("voice.llm.speculation.turn.cue")
                .description("end-of-turn verdicts taken from the speculative reply")
                .tag("verdict", "incomplete")
                .register(registry);
        // waitForCaller followed by silence: the model judged the caller mid-thought, they were
        // not, and the bot answered after DIALOG_WAIT_FOR_CALLER_TIMEOUT_MS. A high rate means
        // the model waits too readily — each one cost the caller that much silence.
        this.waitForCallerTimeouts = Counter.builder("voice.llm.wait.for.caller.timeout")
                .description("waitForCaller turns the caller never followed up, answered after the timeout")
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
        // How big one turn's request is (human-like call plan, phase 0). A prompt that keeps
        // growing — knowledge, memory, stage rules — slows every turn and, past the model's
        // window, is cut silently by the provider; these show the drift before it hurts.
        // Untagged by stage on purpose: stage names are tenant-defined, an unbounded tag set.
        this.systemPromptChars = DistributionSummary.builder("voice.llm.prompt.system.chars")
                .description("characters in the system prompt of one caller turn's request")
                .publishPercentileHistogram()
                .register(registry);
        this.turnPromptTokens = DistributionSummary.builder("voice.llm.prompt.tokens")
                .description("prompt tokens the provider reported for one caller turn's first request")
                .publishPercentileHistogram()
                .register(registry);
        // Should stay near zero. A rise is the prompt drifting past what it was sized for —
        // find which stage in the "llm request" log line before raising the budget.
        this.promptsOverBudget = Counter.builder("voice.llm.prompt.over.budget")
                .description("caller turns whose prompt tokens went over voice-agent.dialog.prompt-budget-tokens")
                .register(registry);
        this.llmTurn = Timer.builder("voice.llm.turn.latency").publishPercentileHistogram().register(registry);
    }

    /**
     * The size of one caller turn's request: the system prompt we built, and the prompt tokens
     * the provider counted for it ({@code 0} when it reported none — not recorded then).
     *
     * @param budgetTokens the prompt tokens a turn should stay under; 0 = no budget
     * @return whether the turn went over the budget (counted in {@code voice.llm.prompt.over.budget})
     */
    public boolean recordTurnPrompt(int systemChars, long promptTokens, int budgetTokens) {
        systemPromptChars.record(systemChars);
        if (promptTokens <= 0) {
            return false;
        }
        turnPromptTokens.record(promptTokens);
        if (budgetTokens <= 0 || promptTokens <= budgetTokens) {
            return false;
        }
        promptsOverBudget.increment();
        return true;
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

    /** The speculative reply's first chunk said whether the caller had finished. */
    public void speculationTurnCue(boolean complete) {
        (complete ? turnCuesComplete : turnCuesIncomplete).increment();
    }

    /** A waitForCaller the caller never followed up was answered after the timeout. */
    public void waitForCallerTimeout() {
        waitForCallerTimeouts.increment();
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
