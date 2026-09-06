package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer metrics for the voice pipeline (PROJECT.md §10 Bosqich 12): call and
 * disposition counts, STT/TTS/LLM error counters, LLM-turn and TTS-synthesis
 * latency timers, inbound RTP quality, call duration, and a live active-call gauge.
 * Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class VoiceMetrics {

    /** The percentile the §1.3 turnaround budget is judged on. */
    private static final double SLO_PERCENTILE = 0.95;

    private final MeterRegistry registry;
    private final Counter calls;
    private final Counter sttErrors;
    private final Counter ttsErrors;
    private final Counter llmErrors;
    private final Counter ttsCacheHits;
    private final Counter ttsCacheMisses;
    private final Counter ttsCharsSynthesized;
    private final Counter ttsCharsSaved;
    private final Counter speculationsStarted;
    private final Counter speculationsHit;
    private final Counter speculationsMissed;
    private final Counter llmPromptTokens;
    private final Counter llmCompletionTokens;
    private final Counter llmCachedTokens;
    private final Counter sttSecondsSent;
    private final Counter sttSecondsSkipped;
    private final Counter sttUtterancesEndpointed;
    private final Counter sttFinalsPromoted;
    private final Counter noInputPrompts;
    private final Counter noInputHangups;
    private final Counter factGuardBlocks;
    private final Counter factGuardSpokenFigures;
    private final Counter voicemailsDetected;
    private final Counter spokenLineRetries;
    private final Counter llmRetries;
    private final Counter fillersPlayed;
    private final Counter bargeIns;
    private final Counter falseBargeIns;
    private final Counter stitchedTurns;
    private final Counter backchannels;
    private final Counter backchannelsPlayed;
    private final Counter knowledgeBaseAnswers;
    private final Counter interjections;
    private final Counter fastPathTurns;
    private final Counter echoSuppressed;
    private final Counter ttsFailovers;
    private final Counter rtpPacketsReceived;
    private final Counter rtpPacketsLost;
    private final Counter rtpPacketsReordered;
    private final Counter rtpSilentCalls;
    private final DistributionSummary rtpJitter;
    private final DistributionSummary rtpLoss;
    private final DistributionSummary endpointingHangover;
    private final DistributionSummary qualityScore;
    private final Counter turnsExtended;
    private final Counter turnsComplete;
    private final Counter turnsClosedEarly;
    private final Timer llmTurn;
    private final Timer ttsSynth;
    private final Timer turnaround;
    private final Timer callDuration;
    private final AtomicInteger active = new AtomicInteger(0);

    public VoiceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.calls = Counter.builder("voice.calls.total").register(registry);
        this.sttErrors = Counter.builder("voice.stt.errors").register(registry);
        this.ttsErrors = Counter.builder("voice.tts.errors").register(registry);
        this.llmErrors = Counter.builder("voice.llm.errors").register(registry);
        this.ttsCacheHits = Counter.builder("voice.tts.cache.hits").register(registry);
        this.ttsCacheMisses = Counter.builder("voice.tts.cache.misses").register(registry);
        // Yandex bills TTS per character, so characters — not requests — are the cost
        // unit. "saved" is what a cache hit did not have to buy.
        this.ttsCharsSynthesized = Counter.builder("voice.tts.chars.synthesized")
                .description("characters actually sent to a TTS provider (billed)")
                .register(registry);
        this.ttsCharsSaved = Counter.builder("voice.tts.chars.saved")
                .description("characters served from cache instead of being synthesized")
                .register(registry);
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
        // Yandex bills STT per streamed audio-second; "skipped" is what VAD gating kept
        // off the wire.
        this.sttSecondsSent = Counter.builder("voice.stt.audio.seconds.sent")
                .description("audio seconds streamed to the STT provider (billed)")
                .register(registry);
        this.sttSecondsSkipped = Counter.builder("voice.stt.audio.seconds.skipped")
                .description("audio seconds withheld from the STT provider by VAD gating")
                .register(registry);
        // Zero while the provider's own detector is in charge. Once it isn't, this should
        // track the number of caller turns: far fewer means utterances are being run
        // together, far more means they are being chopped up.
        this.sttUtterancesEndpointed = Counter.builder("voice.stt.utterances.endpointed")
                .description("utterances this side declared finished, instead of the provider")
                .register(registry);
        this.sttFinalsPromoted = Counter.builder("voice.stt.finals.promoted")
                .description("utterances whose last interim was taken as the final because the provider's was late")
                .register(registry);
        // Flat at post-roll-ms unless the adaptation is on. Once it is, this is the whole
        // story: a distribution that never leaves the maximum means callers keep talking
        // through the closes, and one that sits on the floor means the floor is too high
        // to be worth the setting.
        this.endpointingHangover = DistributionSummary.builder("voice.stt.endpointing.hangover")
                .description("silence a long utterance had to wait out before it was closed")
                .baseUnit("milliseconds")
                .publishPercentileHistogram()
                .register(registry);
        this.qualityScore = DistributionSummary.builder("voice.qa.score")
                .description("automated quality review of a finished call, 0-100")
                .publishPercentileHistogram()
                .register(registry);
        // Utterances Smart Turn said were not finished when the timer wanted to close
        // them. All of them means the model disagrees with the timer on every turn, which
        // is a threshold problem, not a caller problem; none of them means it is adding
        // latency to load a model that changes nothing.
        this.turnsExtended = Counter.builder("voice.stt.turn.extended")
                .description("utterances given extra silence because they sounded unfinished")
                .register(registry);
        this.turnsClosedEarly = Counter.builder("voice.stt.turn.closed.early")
                .description("utterances closed before their hangover ran out, on the detector's say-so")
                .register(registry);
        this.turnsComplete = Counter.builder("voice.stt.turn.complete")
                .description("utterances the turn detector agreed were finished")
                .register(registry);
        // A rising prompt count means callers are going quiet — a recognizer that stopped
        // emitting finals looks exactly like this, so it is worth watching.
        this.noInputPrompts = Counter.builder("voice.dialog.no.input.prompts")
                .description("times the bot asked whether the caller was still there")
                .register(registry);
        this.noInputHangups = Counter.builder("voice.dialog.no.input.hangups")
                .description("calls ended because the line stayed silent")
                .register(registry);
        // Should be zero. Anything else is the model trying to state a figure that is not
        // in the caller's file (§4.4) — alert on it rather than watch it.
        this.factGuardBlocks = Counter.builder("voice.dialog.fact.guard.blocks")
                .description("sentences withheld because their figures did not match the call facts")
                .register(registry);
        // A different event from the one above, and a worse one: this figure was not
        // withheld, it was spoken. A realtime engine talks straight from audio, so the
        // guard only ever sees the transcript of what the caller has already heard.
        // Blocks are the system working; these are incidents.
        this.factGuardSpokenFigures = Counter.builder("voice.dialog.fact.guard.spoken")
                .description("figures spoken on a realtime call that did not match the call facts")
                .register(registry);
        this.voicemailsDetected = Counter.builder("voice.calls.voicemail.detected")
                .description("calls cut short because an answering machine picked up")
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
        // How often a turn was slow enough that the caller was given something to listen
        // to. Read as a share of voice.llm.turn.latency's count: a few percent is the
        // feature working, most turns means the pipeline is slow and this is papering
        // over it — fix the latency, do not lengthen the filler.
        this.fillersPlayed = Counter.builder("voice.dialog.filler.played")
                .description("turns where a short filler covered the wait for the LLM")
                .register(registry);
        // How often a caller talked the bot down mid-reply. A campaign where this is near
        // zero is not one nobody interrupts — it is one where barge-in is not reaching the
        // engine at all (no VAD model, a detector that never re-arms), which is invisible
        // otherwise: the caller simply experiences a bot that talks over them.
        this.bargeIns = Counter.builder("voice.dialog.barge.in")
                .description("replies a caller interrupted mid-utterance")
                .register(registry);
        // Barge-ins the recognizer never backed up with words — a cough, a door, or the
        // bot's own audio echoing back off a speakerphone. Read as a share of the counter
        // above: a majority means the VAD is firing on noise, or there is echo on the line.
        this.falseBargeIns = Counter.builder("voice.dialog.barge.in.false")
                .description("interruptions no transcript followed, after which the reply resumed")
                .register(registry);
        // The caller was still talking when the gate closed: the turn started, was cancelled
        // before a word of the reply went out, and the next final was folded onto the first.
        // This is the cut-off rate the endpointing settings are tuned against — every one
        // of these is a sentence the wait was too short for.
        this.stitchedTurns = Counter.builder("voice.dialog.turn.stitched")
                .description("caller utterances the gate split in two and the dialog rejoined")
                .register(registry);
        // Barge-ins the caller did back up with words, but only "aha" — agreement over the
        // top of the bot, not an answer to it. Counted separately from the false ones
        // because the cause is different: these are not noise, and no amount of VAD
        // tuning removes them.
        this.fastPathTurns = Counter.builder("voice.dialog.fastpath.turns")
                .description("turns settled deterministically, without a model call")
                .register(registry);
        this.interjections = Counter.builder("voice.dialog.interjections")
                .description("times the bot stepped into an answer that would not end")
                .register(registry);
        this.knowledgeBaseAnswers = Counter.builder("voice.dialog.knowledge.answers")
                .description("turns answered from the fixed knowledge base instead of the model")
                .register(registry);
        this.backchannelsPlayed = Counter.builder("voice.dialog.backchannel.played")
                .description("times the bot said \"aha\" under a caller who was still talking")
                .register(registry);
        this.backchannels = Counter.builder("voice.dialog.backchannel.ignored")
                .description("caller agreement over the bot's line that did not start a turn")
                .register(registry);
        // Caller finals thrown away for repeating what the bot had just said. Non-zero
        // means the far end has no echo cancellation and the agent was about to answer
        // itself; the fix is on the Asterisk side, not here.
        this.echoSuppressed = Counter.builder("voice.dialog.echo.suppressed")
                .description("caller transcripts dropped as an echo of the bot's own line")
                .register(registry);
        // A provider outage that the substitute covered. Never zero for long without
        // someone looking: the caller is hearing a different voice than the campaign chose.
        this.ttsFailovers = Counter.builder("voice.tts.failovers")
                .description("lines a second TTS provider spoke after the first one failed")
                .register(registry);
        // Inbound stream quality (RFC 3550). The counters are the fleet-wide view; the
        // two summaries below are per call, which is where a handful of bad calls inside
        // an otherwise healthy total becomes visible.
        this.rtpPacketsReceived = Counter.builder("voice.rtp.packets.received").register(registry);
        this.rtpPacketsLost = Counter.builder("voice.rtp.packets.lost")
                .description("packets the sequence numbers say the network never delivered")
                .register(registry);
        this.rtpPacketsReordered = Counter.builder("voice.rtp.packets.reordered")
                .description("packets that arrived late or twice")
                .register(registry);
        // Should be zero. Anything else is a media path that was never established —
        // the caller talked to a bot that could not hear a thing (docs/NETWORK.md).
        this.rtpSilentCalls = Counter.builder("voice.rtp.calls.silent")
                .description("calls that ended without a single inbound RTP packet")
                .register(registry);
        this.rtpJitter = DistributionSummary.builder("voice.rtp.jitter")
                .description("interarrival jitter of one call's inbound RTP")
                .baseUnit("milliseconds")
                .publishPercentileHistogram()
                .register(registry);
        this.rtpLoss = DistributionSummary.builder("voice.rtp.loss")
                .description("packet loss of one call's inbound RTP")
                .baseUnit("percent")
                .publishPercentileHistogram()
                .register(registry);
        this.llmTurn = Timer.builder("voice.llm.turn.latency").publishPercentileHistogram().register(registry);
        this.ttsSynth = Timer.builder("voice.tts.synth.latency").register(registry);
        // The number §1.3 actually budgets (<1000ms): client stopped speaking -> first
        // bot audio on the wire. The LLM and TTS timers alone cannot show it, because
        // sentence streaming overlaps them.
        this.turnaround = Timer.builder("voice.turnaround.latency")
                .description("client final transcript -> first byte of bot audio queued")
                .publishPercentileHistogram()
                // Also computed in-process, because the §1.3 budget is checked here
                // (AlertingService) and not only in whatever scrapes Prometheus.
                .publishPercentiles(SLO_PERCENTILE)
                .register(registry);
        this.callDuration = Timer.builder("voice.call.duration").register(registry);
        Gauge.builder("voice.calls.active", active, AtomicInteger::get).register(registry);
    }

    public void callStarted() {
        calls.increment();
    }

    public void activeInc() {
        active.incrementAndGet();
    }

    public void activeDec() {
        active.updateAndGet(n -> n > 0 ? n - 1 : 0);
    }

    public int activeCalls() {
        return active.get();
    }

    /** Count the final disposition, tagged for a distribution breakdown. */
    public void disposition(Disposition d) {
        Counter.builder("voice.calls.disposition")
                .tag("disposition", d != null ? d.name() : "UNKNOWN")
                .register(registry)
                .increment();
    }

    public void sttError() {
        sttErrors.increment();
    }

    /**
     * A live call moved off a recognizer that kept failing
     * ({@link uz.murodjon.robotcallv2.agent.stt.SttStreamBridge}). Tagged both ways: the
     * question this answers during an incident is which vendor is down, and one that only
     * counted events would need the logs to say.
     */
    public void sttFailover(String from, String to) {
        Counter.builder("voice.stt.failover")
                .description("calls moved to another STT provider mid-call after repeated failures")
                .tag("from", from != null ? from : "unknown")
                .tag("to", to != null ? to : "unknown")
                .register(registry)
                .increment();
    }

    public void ttsError() {
        ttsErrors.increment();
    }

    public void ttsCacheHit() {
        ttsCacheHits.increment();
    }

    public void ttsCacheMiss() {
        ttsCacheMisses.increment();
    }

    /** Characters a provider was actually asked to synthesize (and billed for). */
    public void ttsCharsSynthesized(int chars) {
        if (chars > 0) {
            ttsCharsSynthesized.increment(chars);
        }
    }

    /** Characters a cache hit spared us from buying. */
    public void ttsCharsSaved(int chars) {
        if (chars > 0) {
            ttsCharsSaved.increment(chars);
        }
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

    /** A short filler was played because the turn was leaving the caller in silence. */
    public void fillerPlayed() {
        fillersPlayed.increment();
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

    /** Audio streamed to STT, in seconds — the unit Yandex bills. */
    public void sttAudioSent(double seconds) {
        if (seconds > 0) {
            sttSecondsSent.increment(seconds);
        }
    }

    /** Audio VAD gating kept off the STT stream, in seconds. */
    public void sttAudioSkipped(double seconds) {
        if (seconds > 0) {
            sttSecondsSkipped.increment(seconds);
        }
    }

    /** We told the recognizer an utterance was over instead of letting it decide (§1.3). */
    public void sttUtteranceEnded() {
        sttUtterancesEndpointed.increment();
    }

    /** The provider's final was late and the last interim was used in its place. */
    public void sttFinalPromoted() {
        sttFinalsPromoted.increment();
    }

    /** The wait a long utterance earned before it was closed — fixed, or learned. */
    public void sttEndpointingHangover(int ms) {
        endpointingHangover.record(ms);
    }

    /** The turn detector's verdict on one utterance the timer was ready to close. */
    public void turnScored(boolean complete) {
        (complete ? turnsComplete : turnsExtended).increment();
    }

    /**
     * An utterance the detector was sure of, closed before its hangover had run out. The
     * saving is only real while callers are not talking through these closes, so read it
     * against the turn count and against how often callers get a second turn they did not
     * mean to start.
     */
    public void turnClosedEarly() {
        turnsClosedEarly.increment();
    }

    /** The bot asked whether the caller was still there. */
    public void noInputPrompt() {
        noInputPrompts.increment();
    }

    /** A call ended because nobody spoke. */
    public void noInputHangup() {
        noInputHangups.increment();
    }

    /** A sentence was withheld because its figures did not match the call facts (§4.4). */
    public void factGuardBlock() {
        factGuardBlocks.increment();
    }

    /**
     * A figure that is not in the call's facts was <em>spoken</em> on a realtime call
     * (§4.4). Unlike {@link #factGuardBlock}, nothing was prevented — alert on this.
     */
    public void factGuardSpoken() {
        factGuardSpokenFigures.increment();
    }

    /** An answering machine was detected and the call cut short (§8.6). */
    public void voicemailDetected() {
        voicemailsDetected.increment();
    }

    /** The caller spoke over the bot and the rest of the reply was dropped (§7.2). */
    public void bargeIn() {
        bargeIns.increment();
    }

    /** A barge-in no transcript followed, so the interrupted reply was resumed. */
    public void falseBargeIn() {
        falseBargeIns.increment();
    }

    /** The gate closed mid-sentence; the two halves were answered as one turn. */
    public void turnStitched() {
        stitchedTurns.increment();
    }

    /** A caller transcript was agreement over the bot's line, so it did not start a turn. */
    public void backchannelIgnored() {
        backchannels.increment();
    }

    /** The bot said "aha" under a caller who was still explaining something. */
    public void backchannelPlayed() {
        backchannelsPlayed.increment();
    }

    /**
     * One finished call's quality score, as judged by a second model
     * ({@code CallQualityJudge}). A distribution rather than an average: what matters is
     * the tail, and a handful of very bad calls is exactly what an average hides.
     */
    public void recordQualityScore(int score) {
        qualityScore.record(score);
    }

    /**
     * One thing the judge found wrong with a call. Tagged so a regression reads as one
     * flag rising — a prompt change that starts answering in the wrong language moves
     * {@code language} and nothing else.
     */
    public void qualityFlag(String flag) {
        Counter.builder("voice.qa.flags")
                .description("findings from the automated call-quality review")
                .tag("flag", flag)
                .register(registry)
                .increment();
    }

    /** A turn settled by the deterministic router — no model call, no tokens. */
    public void fastPathHandled() {
        fastPathTurns.increment();
    }

    /** The bot stepped into an answer that had run past interject-after-ms. */
    public void interjected() {
        interjections.increment();
    }

    /** A turn answered from the fixed knowledge base — no model call, no tokens. */
    public void knowledgeBaseAnswer() {
        knowledgeBaseAnswers.increment();
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

    /** A caller transcript was dropped for echoing the bot's own line back at it. */
    public void echoSuppressed() {
        echoSuppressed.increment();
    }

    /** A line the preferred TTS provider failed to speak was spoken by another one. */
    public void ttsFailover() {
        ttsFailovers.increment();
    }

    /**
     * Record one finished call's inbound RTP quality. A call with no packets at all is
     * only counted as silent: it has no loss ratio (nothing was expected either) and no
     * jitter, and averaging those zeros in would hide exactly the failure being counted.
     */
    public void recordRtpQuality(long received, long lost, long reordered, double jitterMillis) {
        if (received <= 0) {
            rtpSilentCalls.increment();
            return;
        }
        rtpPacketsReceived.increment(received);
        if (lost > 0) {
            rtpPacketsLost.increment(lost);
        }
        if (reordered > 0) {
            rtpPacketsReordered.increment(reordered);
        }
        rtpJitter.record(jitterMillis);
        rtpLoss.record(lost * 100.0 / (received + lost));
    }

    /**
     * Running totals behind the efficiency ratios — speculations begun and confirmed, TTS
     * characters bought and saved, prompt tokens sent and served from the provider's
     * cache. Read as deltas between two checks: each one is the numerator or the
     * denominator of a ratio that says whether an optimization is paying for itself.
     */
    public long speculationStartedCount() {
        return (long) speculationsStarted.count();
    }

    public long speculationHitCount() {
        return (long) speculationsHit.count();
    }

    public long ttsCacheHitCount() {
        return (long) ttsCacheHits.count();
    }

    public long ttsCacheMissCount() {
        return (long) ttsCacheMisses.count();
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

    public void stopTtsSynth(Timer.Sample sample) {
        sample.stop(ttsSynth);
    }

    /** Record how long the caller waited between finishing their turn and hearing the bot. */
    public void recordTurnaround(Duration elapsed) {
        turnaround.record(elapsed);
    }

    /**
     * One stage of one turn's wait ({@link TurnLatency}). Tagged rather than one meter per
     * stage so the breakdown can be read as a single stacked distribution — which stage
     * dominates is the only question this answers, and it is the one worth asking before
     * anything in the pipeline is tuned.
     */
    public void recordTurnStage(String stage, long millis) {
        Timer.builder("voice.turn.stage.latency")
                .description("one stage of the wait between the caller finishing and hearing the bot")
                .tag("stage", stage)
                .publishPercentileHistogram()
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * The 95th percentile turnaround in milliseconds, or {@code 0} before anything has
     * been measured. Micrometer keeps this over a rolling window (its default statistic
     * expiry), so it answers "how is the pipeline doing now", not "since startup" — which
     * is what an alert needs.
     */
    public double turnaroundP95Millis() {
        for (ValueAtPercentile value : turnaround.takeSnapshot().percentileValues()) {
            if (value.percentile() == SLO_PERCENTILE) {
                return value.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0;
    }

    /**
     * Turns measured since startup. Cumulative on purpose: the caller compares it against
     * its own previous reading to learn how many turns the latest window actually holds,
     * which no windowed count of ours could tell it.
     */
    public long turnaroundCount() {
        return turnaround.count();
    }

    public void recordCallDuration(long seconds) {
        callDuration.record(Duration.ofSeconds(Math.max(0, seconds)));
    }
}
