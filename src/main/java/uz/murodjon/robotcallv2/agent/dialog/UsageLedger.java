package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.agent.metrics.TurnTiming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What one call has spent and how fast it answered: LLM tokens, synthesized characters,
 * turn and LLM latency, and fact-guard violations.
 *
 * <p>Token/latency breakdown for the "Texnik" tab (§10.5) — Micrometer's
 * voice_llm_tokens_*&#47;voice_llm_turn_latency metrics are global counters with no
 * per-call correlation (see ROADMAP), so this call's numbers have to be accumulated
 * here and read once at teardown, before the session is dropped.
 *
 * <p>Thread-safety: every counter is atomic. A sum and its count are updated one after
 * the other, not together; they are only read at teardown.
 */
class UsageLedger {

    /**
     * Running total of LLM tokens this call has spent — what the per-call budget
     * (§C14) is checked against. A call that loops instead of ending would otherwise
     * cost without bound.
     */
    private final AtomicLong tokensUsed = new AtomicLong();

    /** Money figures the fact guard refused to speak (§4.4); for the log and metrics. */
    private final AtomicInteger factViolations = new AtomicInteger();

    /** Times the caller cut the agent off — a quality signal (UNIVERSAL-OPERATOR-PLAN D.1). */
    private final AtomicInteger bargeIns = new AtomicInteger();
    /** Caller utterances the recognizer was unsure of — the same. */
    private final AtomicInteger lowConfidenceInputs = new AtomicInteger();

    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong cachedTokens = new AtomicLong();
    /**
     * Characters this call asked a synthesizer for — what the call is billed for.
     *
     * <p>Counted where the text is handed to the router, so a sentence the TTS cache
     * answers is counted too. That is deliberate: the company is billed for the speech
     * its agent produced, and the cache is this platform's margin, measured separately as
     * {@code voice.tts.chars.saved}.
     */
    private final AtomicLong ttsChars = new AtomicLong();
    private final AtomicLong turnLatencySumMs = new AtomicLong();
    private final AtomicInteger turnLatencyCount = new AtomicInteger();
    private final AtomicLong turnLatencyMaxMs = new AtomicLong();
    private final AtomicLong llmLatencySumMs = new AtomicLong();
    private final AtomicInteger llmLatencyCount = new AtomicInteger();
    private final AtomicLong llmLatencyMaxMs = new AtomicLong();
    /** Per-turn stage timings for the Audio Inspector, oldest first, at most {@link #MAX_TURN_TIMINGS}. */
    private final List<TurnTiming> turnTimings = Collections.synchronizedList(new ArrayList<>());

    /** Far beyond any real call; a call stuck in a loop must not grow this without bound. */
    private static final int MAX_TURN_TIMINGS = 500;

    /** Add a turn's token usage to the call total and return the new total. */
    long addTokens(long tokens) {
        return tokens > 0 ? tokensUsed.addAndGet(tokens) : tokensUsed.get();
    }

    long tokensUsed() {
        return tokensUsed.get();
    }

    /** Add one turn's token breakdown to the call's running totals (§10.5 "Texnik" tab). */
    void addTokenBreakdown(long prompt, long completion, long cached) {
        if (prompt > 0) {
            promptTokens.addAndGet(prompt);
        }
        if (completion > 0) {
            completionTokens.addAndGet(completion);
        }
        if (cached > 0) {
            cachedTokens.addAndGet(cached);
        }
    }

    long promptTokens() {
        return promptTokens.get();
    }

    long completionTokens() {
        return completionTokens.get();
    }

    long cachedTokens() {
        return cachedTokens.get();
    }

    /** Count one line's characters towards what this call is billed for. */
    void addTtsChars(String text) {
        if (text != null && !text.isBlank()) {
            ttsChars.addAndGet(text.length());
        }
    }

    long ttsChars() {
        return ttsChars.get();
    }

    /** Record one turn's client-stopped-talking -> first-audio-queued latency. */
    void recordTurnLatency(long ms) {
        turnLatencySumMs.addAndGet(ms);
        turnLatencyCount.incrementAndGet();
        turnLatencyMaxMs.accumulateAndGet(ms, Math::max);
    }

    Integer avgTurnLatencyMs() {
        int count = turnLatencyCount.get();
        return count == 0 ? null : (int) (turnLatencySumMs.get() / count);
    }

    Integer maxTurnLatencyMs() {
        return turnLatencyCount.get() == 0 ? null : (int) turnLatencyMaxMs.get();
    }

    void recordTurnTiming(TurnTiming timing) {
        if (turnTimings.size() < MAX_TURN_TIMINGS) {
            turnTimings.add(timing);
        }
    }

    List<TurnTiming> turnTimings() {
        synchronized (turnTimings) {
            return List.copyOf(turnTimings);
        }
    }

    /** Record one turn's LLM-only wall time. */
    void recordLlmLatency(long ms) {
        llmLatencySumMs.addAndGet(ms);
        llmLatencyCount.incrementAndGet();
        llmLatencyMaxMs.accumulateAndGet(ms, Math::max);
    }

    Integer avgLlmLatencyMs() {
        int count = llmLatencyCount.get();
        return count == 0 ? null : (int) (llmLatencySumMs.get() / count);
    }

    Integer maxLlmLatencyMs() {
        return llmLatencyCount.get() == 0 ? null : (int) llmLatencyMaxMs.get();
    }

    int recordFactViolation() {
        return factViolations.incrementAndGet();
    }

    int factViolations() {
        return factViolations.get();
    }

    void recordBargeIn() {
        bargeIns.incrementAndGet();
    }

    int bargeIns() {
        return bargeIns.get();
    }

    void recordLowConfidenceInput() {
        lowConfidenceInputs.incrementAndGet();
    }

    int lowConfidenceInputs() {
        return lowConfidenceInputs.get();
    }
}
