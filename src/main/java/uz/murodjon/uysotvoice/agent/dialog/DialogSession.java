package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.ai.chat.messages.Message;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.shared.dialog.DialogState;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable in-memory state for one call's conversation (PROJECT.md §4, §5.1). Holds
 * the FSM state, the chat history sent to the LLM each turn, guardrail counters, and
 * the outputs a turn's tool calls record. Not thread-safe by itself — {@code busy}
 * serializes turns and the engine mutates it on a single worker thread at a time.
 */
public class DialogSession {

    private final String channelId;
    private final String language;
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
    private int turnCount;

    // Recorded by tool calls; consumed by the Stage 9 summary/CRM step.
    private Disposition disposition;
    private ReasonCode reasonCode;
    private LocalDate promisedDate;
    private BigDecimal promisedAmount;

    public DialogSession(String channelId, String language, CallContext context,
                         RtpEndpoint endpoint, Runnable hangup, Runnable transfer, long callAttemptId) {
        this.channelId = channelId;
        this.language = language;
        this.context = context;
        this.endpoint = endpoint;
        this.hangup = hangup;
        this.transfer = transfer;
        this.callAttemptId = callAttemptId;
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

    public void setPromisedAmount(BigDecimal promisedAmount) {
        this.promisedAmount = promisedAmount;
    }
}
