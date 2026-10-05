package uz.murodjon.robotcallv2.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Conversation metrics of the cascade pipeline: turnaround and its stages, silence
 * handling, the fact guard, barge-ins, backchannels and the turns settled without a model
 * call. Exposed via Actuator at {@code /actuator/prometheus}.
 */
@Component
public class DialogMetrics {

    /** The percentile the §1.3 turnaround budget is judged on. */
    static final double SLO_PERCENTILE = 0.95;

    private final MeterRegistry registry;
    private final Counter noInputPrompts;
    private final Counter noInputHangups;
    private final Counter factGuardBlocks;
    private final Counter factGuardSpokenFigures;
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
    private final Timer turnaround;

    public DialogMetrics(MeterRegistry registry) {
        this.registry = registry;
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
    }

    /** A short filler was played because the turn was leaving the caller in silence. */
    public void fillerPlayed() {
        fillersPlayed.increment();
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

    /** A caller transcript was dropped for echoing the bot's own line back at it. */
    public void echoSuppressed() {
        echoSuppressed.increment();
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
}
