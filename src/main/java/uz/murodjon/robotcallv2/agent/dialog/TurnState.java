package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.agent.metrics.TurnLatency;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The per-turn machinery of one cascade call ({@link DialogSession} delegates to it): the
 * busy claim that serializes turns, barge-in and deferred input, the turnaround clock,
 * the caller's answer in progress, speculation diagnostics, and the lines a turn's tools
 * carried.
 *
 * <p>Thread-safety: every field is an atomic or a volatile, written on the turn, STT and
 * watchdog threads. The one exception is the answer-timing trio
 * ({@code clientSpeechStartedAt}, {@code backchannelSpoken}, {@code interjected}), which
 * is read and written together and guarded by this object.
 */
class TurnState {

    private final AtomicBoolean busy = new AtomicBoolean(false);

    /**
     * Client speech that arrived while a turn was already running. Held rather than
     * dropped: an LLM turn takes a second or two, and anything the client said in that
     * window is exactly the input the next turn needs.
     */
    private final AtomicReference<String> pendingInput = new AtomicReference<>();

    /**
     * Raised by barge-in. A streaming turn checks it between chunks so the sentences
     * the client talked over are never synthesized — the caller has moved on, and
     * speaking them would talk over the client in turn.
     *
     * <p>When a barge-in cancelled the reply in flight ({@link System#nanoTime()}), or 0
     * while nothing has.
     */
    private final AtomicLong cancelledAt = new AtomicLong(0);

    private volatile boolean interrupted = false;

    /** Written on the turn thread, read by the watchdog — hence volatile. */
    private volatile int turnCount;

    /**
     * Set when the client's turn ends, cleared once the bot's first audio for that
     * turn is queued — the two ends of the &lt;1s turnaround budget (§1.3). Null for
     * the opening greeting, where nobody was waiting.
     */
    private volatile Instant turnStartedAt;
    private final AtomicBoolean turnaroundRecorded = new AtomicBoolean(true);

    /** Where this turn's wait went, stage by stage — the breakdown behind that budget. */
    private final TurnLatency latency = new TurnLatency();

    /** When the answer the caller is currently giving began, or null between answers. Guarded by {@code this}. */
    private Instant clientSpeechStartedAt;
    /** Whether that answer has already had its one "aha" (§ backchannel-after-ms). */
    private volatile boolean backchannelSpoken;
    /** Whether the bot has already stepped into that answer (§ interject-after-ms). Guarded by {@code this}. */
    private boolean interjected;

    /**
     * Whether the recognizer said it was unsure of the words this turn is answering. The
     * turn annex then tells the model to confirm before it acts on them — a misheard date
     * that reaches a tool is a promise recorded for a day nobody named.
     */
    private volatile boolean lowConfidenceInput;

    /**
     * Passages from the company's knowledge base that bear on what the caller just said,
     * looked up once per turn and put in the turn annex. Empty when the agent has RAG
     * switched off, when nothing matched, or when the embedding model was unreachable —
     * the turn then runs exactly as it did before there was a knowledge base.
     */
    private volatile List<KnowledgePassage> knowledgePassages = List.of();

    /**
     * A caller utterance whose turn was cancelled before one word of the reply went out:
     * the gate closed, the caller was in fact still talking, and their next final is the
     * rest of the same sentence. {@code TurnRunner} folds the two into one turn.
     */
    private final AtomicReference<String> unansweredClientText = new AtomicReference<>();

    /** When the recognizer last produced an interim — the caller was speaking then. */
    private volatile long lastInterimAtMs;

    /** Keys have been pressed and the typed sequence has not been handed over as a turn yet. */
    private volatile boolean typing;

    /**
     * A reply started while the caller was still speaking, waiting to find out whether
     * they said what the recognizer guessed they were saying ({@link Speculation}).
     */
    private final AtomicReference<Speculation> speculation = new AtomicReference<>();

    /**
     * What happened to this turn's guessing, for the one summary line the turn logs
     * ({@code SpeculationManager#adoptSpeculation}). An utterance produces a dozen interims
     * and logging each of them buries the call; a count and the last reason one was turned
     * away answer the same question in a line.
     */
    private final AtomicInteger interimsThisTurn = new AtomicInteger();
    private volatile String speculationBlocker;

    /**
     * What this turn's tool calls said should be spoken, in the order they were called.
     *
     * <p>A tool-calling turn comes back from the provider as function calls and nothing
     * else — no text — and the caller is then listening to silence while a second request
     * asks for the line (a whole extra round trip inside the §1.3 budget). So every tool
     * carries its line as an argument instead ({@link DialogTools}), and the engine speaks
     * what the tools brought with them.
     */
    private final List<String> toolReplies = new CopyOnWriteArrayList<>();

    /** Sentences in a row the synthesizer failed on; any sentence it speaks resets it. */
    private final AtomicInteger ttsFailures = new AtomicInteger();

    AtomicBoolean busy() {
        return busy;
    }

    /** Queue client speech that could not be handled now; merged with anything already waiting. */
    void deferInput(String text) {
        pendingInput.accumulateAndGet(text, (existing, added) ->
                existing == null || existing.isBlank() ? added : existing + " " + added);
    }

    /** Take the deferred client speech, or {@code null} if there is none. */
    String takeDeferredInput() {
        return pendingInput.getAndSet(null);
    }

    boolean isCancelled() {
        return cancelledAt.get() != 0;
    }

    void setCancelled(boolean cancelled) {
        long now = System.nanoTime();
        this.cancelledAt.set(cancelled ? (now == 0 ? 1 : now) : 0);
    }

    /**
     * Clears a cancellation made before {@code startNanos} and keeps one made after it, in
     * one atomic step — so a barge-in that races the start of a turn is not wiped by it.
     */
    void clearCancelledBefore(long startNanos) {
        cancelledAt.getAndUpdate(at -> at != 0 && at - startNanos < 0 ? 0 : at);
    }

    boolean isInterrupted() {
        return interrupted;
    }

    void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    int turnCount() {
        return turnCount;
    }

    int incrementTurn() {
        return ++turnCount;
    }

    /** Start the turnaround clock — the client just stopped speaking. */
    void startTurnClock() {
        turnStartedAt = Instant.now();
        turnaroundRecorded.set(false);
        latency.turnStarted();
    }

    /**
     * Time the client has been waiting since their turn ended, or {@code null} if the
     * turnaround for this turn was already recorded (or there was nothing to measure,
     * as with the opening greeting). Returns a value at most once per turn.
     */
    Duration takeTurnaround() {
        Instant started = turnStartedAt;
        if (started == null || !turnaroundRecorded.compareAndSet(false, true)) {
            return null;
        }
        return Duration.between(started, Instant.now());
    }

    /**
     * Non-consuming peek at whether {@link #takeTurnaround()} would still return a value —
     * i.e. whether the turn's first audio has not been queued yet. Lets a caller decide
     * *before* synthesizing whether this is the sentence the §1.3 clock is waiting on
     * (streaming-worthy) without tripping the once-per-turn flag itself.
     */
    boolean isFirstAudioPending() {
        return turnStartedAt != null && !turnaroundRecorded.get();
    }

    /** This turn's stage-by-stage stamps (§1.3 breakdown). */
    TurnLatency latency() {
        return latency;
    }

    /**
     * Whether the caller has now been talking for {@code afterMs} without a backchannel,
     * and claims the right to one if so — at most one per answer, so a long explanation
     * gets an "aha" rather than a chorus.
     *
     * <p>Timed from the first interim of the answer, which is the only signal this side
     * has that somebody is still mid-sentence. Reset by the final that ends it.
     */
    synchronized boolean claimBackchannel(int afterMs) {
        if (afterMs <= 0) {
            return false;
        }
        Instant since = clientSpeechStartedAt;
        if (since == null) {
            clientSpeechStartedAt = Instant.now();
            return false;
        }
        if (backchannelSpoken || Duration.between(since, Instant.now()).toMillis() < afterMs) {
            return false;
        }
        backchannelSpoken = true;
        return true;
    }

    /**
     * Whether the caller has now been talking for {@code afterMs} — long past a backchannel
     * — and the bot should step in ({@code SpeechOutput#interject}). Once per answer.
     */
    synchronized boolean claimInterjection(int afterMs) {
        Instant since = clientSpeechStartedAt;
        if (afterMs <= 0 || since == null || interjected) {
            return false;
        }
        if (Duration.between(since, Instant.now()).toMillis() < afterMs) {
            return false;
        }
        interjected = true;
        return true;
    }

    /** The caller finished: the next answer starts its own clock and earns its own "aha". */
    synchronized void clientAnswerEnded() {
        clientSpeechStartedAt = null;
        backchannelSpoken = false;
        interjected = false;
    }

    /** Whether the recognizer was unsure of the words the current turn is answering. */
    boolean isLowConfidenceInput() {
        return lowConfidenceInput;
    }

    void setLowConfidenceInput(boolean lowConfidenceInput) {
        this.lowConfidenceInput = lowConfidenceInput;
    }

    /** Knowledge-base passages the current turn may answer from; never null. */
    List<KnowledgePassage> knowledgePassages() {
        return knowledgePassages;
    }

    void setKnowledgePassages(List<KnowledgePassage> passages) {
        this.knowledgePassages = passages == null ? List.of() : List.copyOf(passages);
    }

    /** Remember a caller utterance the bot never got to answer; {@code null} forgets it. */
    void setUnansweredClientText(String text) {
        unansweredClientText.set(text == null || text.isBlank() ? null : text.trim());
    }

    /** Take the unanswered utterance, or {@code null} if the last turn was answered. */
    String takeUnansweredClientText() {
        return unansweredClientText.getAndSet(null);
    }

    /**
     * Whether the recognizer has heard the caller within the last {@code withinMs} — or the
     * caller is part-way through typing a number, which is input just as much and has no
     * interims to show for it.
     */
    boolean heardCallerWithin(long withinMs) {
        if (typing) {
            return true;
        }
        long at = lastInterimAtMs;
        return at != 0 && System.currentTimeMillis() - at <= withinMs;
    }

    /**
     * Park a speculative reply. Anything it supersedes is cancelled: the caller has said
     * more since, so the older hypothesis is answering half a sentence.
     */
    void setSpeculation(Speculation next) {
        Speculation previous = speculation.getAndSet(next);
        if (previous != null) {
            previous.discard();
        }
    }

    /** Non-consuming peek at the parked speculative reply, or {@code null} if there is none. */
    Speculation speculation() {
        return speculation.get();
    }

    /** Take the parked speculative reply, leaving none behind. */
    Speculation takeSpeculation() {
        return speculation.getAndSet(null);
    }

    /** Cancel and drop any parked speculative reply. */
    void discardSpeculation() {
        Speculation parked = speculation.getAndSet(null);
        if (parked != null) {
            parked.discard();
        }
    }

    /** The recognizer delivered another interim hypothesis for the utterance in progress. */
    void noteInterim() {
        interimsThisTurn.incrementAndGet();
        lastInterimAtMs = System.currentTimeMillis();
    }

    int interimsThisTurn() {
        return interimsThisTurn.get();
    }

    void setTyping(boolean typing) {
        this.typing = typing;
    }

    /** Why the last interim was not worth guessing on, or {@code null} if none was turned away. */
    String speculationBlocker() {
        return speculationBlocker;
    }

    void setSpeculationBlocker(String speculationBlocker) {
        this.speculationBlocker = speculationBlocker;
    }

    /** Start the next turn's count; called once the turn has reported what this one did. */
    void resetSpeculationDiagnostics() {
        interimsThisTurn.set(0);
        speculationBlocker = null;
    }

    /** Record the line a tool call carried; a blank one is no line at all. */
    void addToolReply(String reply) {
        if (reply != null && !reply.isBlank()) {
            toolReplies.add(reply.trim());
        }
    }

    /**
     * This turn's tool-carried lines as one utterance, or {@code null} if the tools
     * brought none. Several tools in one turn read as consecutive sentences — "va'dangizni
     * belgilab qo'ydim" then "xayr" — which is how the model wrote them.
     */
    String toolReplies() {
        return toolReplies.isEmpty() ? null : String.join(" ", toolReplies);
    }

    /** Drop the previous turn's lines before a new turn calls its tools. */
    void clearToolReplies() {
        toolReplies.clear();
    }

    /** @return how many sentences in a row have now failed to synthesize */
    int recordTtsFailure() {
        return ttsFailures.incrementAndGet();
    }

    void clearTtsFailures() {
        ttsFailures.set(0);
    }
}
