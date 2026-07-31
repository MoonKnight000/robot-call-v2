package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.ai.chat.messages.Message;

import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.shared.dialog.DialogState;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable in-memory state for one call's conversation (PROJECT.md §4, §5.1). Holds
 * the FSM state, the chat history sent to the LLM each turn, guardrail counters, and
 * the outputs a turn's tool calls record. Not thread-safe by itself — {@code busy}
 * serializes turns and the engine mutates it on a single worker thread at a time.
 */
public class DialogSession {

    private final String channelId;
    private final String language;
    /** Catalog id of the campaign's chosen voice (§2.5); null = configured routing. */
    private final String ttsVoice;
    /** Silence watchdog for this call; null when the watchdog is disabled. */
    private final NoInputWatchdog watchdog;
    private final CallContext context;
    private final RtpEndpoint endpoint;
    private final Runnable hangup;
    private final Runnable transfer;
    private final long callAttemptId;
    private final List<Message> history = new ArrayList<>();
    private final Instant startedAt = Instant.now();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private volatile DialogState state = DialogState.GREETING;
    private volatile boolean ended = false;
    private volatile boolean interrupted = false;
    private volatile String lastAgentText;
    /**
     * The unchanging half of the prompt, built once on the first turn and then reused
     * byte for byte. Rebuilding it per turn would produce the same text at best, and a
     * different one across midnight — either way the provider's implicit context cache
     * keys on this exact prefix, so it is stored rather than regenerated.
     */
    private volatile String systemPrefix;
    /** Whether the §11.1 disclosure has already been spoken from code this call. */
    private volatile boolean disclosureSpoken;
    private int turnCount;

    /**
     * Set when the client's turn ends, cleared once the bot's first audio for that
     * turn is queued — the two ends of the &lt;1s turnaround budget (§1.3). Null for
     * the opening greeting, where nobody was waiting.
     */
    private volatile Instant turnStartedAt;
    private final AtomicBoolean turnaroundRecorded = new AtomicBoolean(true);

    /**
     * Raised by barge-in. A streaming turn checks it between chunks so the sentences
     * the client talked over are never synthesized — the caller has moved on, and
     * speaking them would talk over the client in turn.
     */
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /**
     * Client speech that arrived while a turn was already running. Held rather than
     * dropped: an LLM turn takes a second or two, and anything the client said in that
     * window is exactly the input the next turn needs.
     */
    private final AtomicReference<String> pendingInput = new AtomicReference<>();

    // Recorded by tool calls; consumed by the Stage 9 summary/CRM step.
    private Disposition disposition;
    private ReasonCode reasonCode;
    private LocalDate promisedDate;
    private BigDecimal promisedAmount;
    /** Why the client asked not to be called again (§11.4); null unless they did. */
    private volatile String doNotCallReason;

    /**
     * Running total of LLM tokens this call has spent — what the per-call budget
     * (§C14) is checked against. A call that loops instead of ending would otherwise
     * cost without bound.
     */
    private final AtomicLong tokensUsed = new AtomicLong();

    /** Money figures the fact guard refused to speak (§4.4); for the log and metrics. */
    private final AtomicInteger factViolations = new AtomicInteger();

    /** Cancels the silence watchdog when the call ends; null when it is disabled. */
    private volatile ScheduledFuture<?> watchdogTask;

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         RtpEndpoint endpoint, Runnable hangup, Runnable transfer, long callAttemptId,
                         NoInputWatchdog watchdog) {
        this.channelId = channelId;
        this.language = language;
        this.ttsVoice = ttsVoice;
        this.context = context;
        this.endpoint = endpoint;
        this.hangup = hangup;
        this.transfer = transfer;
        this.callAttemptId = callAttemptId;
        this.watchdog = watchdog;
    }

    /** The silence watchdog, or {@code null} when it is disabled. */
    public NoInputWatchdog watchdog() {
        return watchdog;
    }

    /** Note that the line is alive, so the watchdog does not count this as silence. */
    public void touchActivity() {
        if (watchdog != null) {
            watchdog.touch(Instant.now());
        }
    }

    public ScheduledFuture<?> watchdogTask() {
        return watchdogTask;
    }

    public void setWatchdogTask(ScheduledFuture<?> watchdogTask) {
        this.watchdogTask = watchdogTask;
    }

    /** Add a turn's token usage to the call total and return the new total. */
    public long addTokens(long tokens) {
        return tokens > 0 ? tokensUsed.addAndGet(tokens) : tokensUsed.get();
    }

    public long tokensUsed() {
        return tokensUsed.get();
    }

    public int recordFactViolation() {
        return factViolations.incrementAndGet();
    }

    public int factViolations() {
        return factViolations.get();
    }

    public Runnable transfer() {
        return transfer;
    }

    public long callAttemptId() {
        return callAttemptId;
    }

    public String channelId() {
        return channelId;
    }

    public String language() {
        return language;
    }

    public String ttsVoice() {
        return ttsVoice;
    }

    public CallContext context() {
        return context;
    }

    public RtpEndpoint endpoint() {
        return endpoint;
    }

    public Runnable hangup() {
        return hangup;
    }

    public List<Message> history() {
        return history;
    }

    public String systemPrefix() {
        return systemPrefix;
    }

    public void setSystemPrefix(String systemPrefix) {
        this.systemPrefix = systemPrefix;
    }

    /**
     * Drop the oldest messages once the history passes {@code maxMessages}, removing
     * {@code trimBlock} at a time.
     *
     * <p>Trimming in blocks rather than one message per turn is deliberate: every trim
     * shifts the start of the history and invalidates the provider's cached prefix, so
     * a long call should pay that penalty a few times, not on every turn. The cap only
     * exists for pathological calls — a normal one never reaches it.
     *
     * @return how many messages were dropped
     */
    public int trimHistory(int maxMessages, int trimBlock) {
        if (maxMessages <= 0 || history.size() <= maxMessages) {
            return 0;
        }
        int drop = Math.min(Math.max(trimBlock, 1), history.size() - 1);
        history.subList(0, drop).clear();
        return drop;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public AtomicBoolean busy() {
        return busy;
    }

    public DialogState state() {
        return state;
    }

    public void setState(DialogState state) {
        this.state = state;
    }

    public boolean isEnded() {
        return ended;
    }

    public void end(Disposition disposition) {
        this.ended = true;
        if (disposition != null) {
            this.disposition = disposition;
        }
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    public boolean isDisclosureSpoken() {
        return disclosureSpoken;
    }

    public void setDisclosureSpoken(boolean disclosureSpoken) {
        this.disclosureSpoken = disclosureSpoken;
    }

    public String lastAgentText() {
        return lastAgentText;
    }

    public void setLastAgentText(String lastAgentText) {
        this.lastAgentText = lastAgentText;
    }

    public int turnCount() {
        return turnCount;
    }

    public int incrementTurn() {
        return ++turnCount;
    }

    /** Start the turnaround clock — the client just stopped speaking. */
    public void startTurnClock() {
        turnStartedAt = Instant.now();
        turnaroundRecorded.set(false);
    }

    /**
     * Time the client has been waiting since their turn ended, or {@code null} if the
     * turnaround for this turn was already recorded (or there was nothing to measure,
     * as with the opening greeting). Returns a value at most once per turn.
     */
    public Duration takeTurnaround() {
        Instant started = turnStartedAt;
        if (started == null || !turnaroundRecorded.compareAndSet(false, true)) {
            return null;
        }
        return Duration.between(started, Instant.now());
    }

    /** Queue client speech that could not be handled now; merged with anything already waiting. */
    public void deferInput(String text) {
        pendingInput.accumulateAndGet(text, (existing, added) ->
                existing == null || existing.isBlank() ? added : existing + " " + added);
    }

    /** Take the deferred client speech, or {@code null} if there is none. */
    public String takeDeferredInput() {
        return pendingInput.getAndSet(null);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled.set(cancelled);
    }

    public Disposition disposition() {
        return disposition;
    }

    public void setDisposition(Disposition disposition) {
        this.disposition = disposition;
    }

    public ReasonCode reasonCode() {
        return reasonCode;
    }

    public void setReasonCode(ReasonCode reasonCode) {
        this.reasonCode = reasonCode;
    }

    public LocalDate promisedDate() {
        return promisedDate;
    }

    public void setPromisedDate(LocalDate promisedDate) {
        this.promisedDate = promisedDate;
    }

    public BigDecimal promisedAmount() {
        return promisedAmount;
    }

    public String doNotCallReason() {
        return doNotCallReason;
    }

    public void setDoNotCallReason(String doNotCallReason) {
        this.doNotCallReason = doNotCallReason;
    }

    public void setPromisedAmount(BigDecimal promisedAmount) {
        this.promisedAmount = promisedAmount;
    }
}
