package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What a call's tool calls have recorded — the part of a conversation's state the
 * platform owns whichever pipeline does the talking, kept the same way by
 * {@link DialogSession} and {@link RealtimeDialogSession}.
 *
 * <p>Thread-safety: written on the turn, tool and watchdog threads and read on the ARI
 * lane at teardown, so every field is volatile or a concurrent collection.
 */
final class CallOutcomeLedger {

    /** Outcome fields a tool call produced (e.g. {@code "promisedDate"}). */
    private final Map<String, Object> outcome = new ConcurrentHashMap<>();
    /** Figures company tools answered with on this call — the fact guard's second source ({@link FactGuard}). */
    private final Set<BigDecimal> toolFigures = ConcurrentHashMap.newKeySet();
    /** How often each company tool has been called on this conversation ({@code Tool.maxCallsPerCall}). */
    private final Map<String, AtomicInteger> toolCallCounts = new ConcurrentHashMap<>();
    /** Write-tool calls read back to the caller and waiting for their yes, by tool name ({@link HttpToolExecutor}). */
    private final Map<String, PendingToolConfirmation> pendingConfirmations = new ConcurrentHashMap<>();
    private volatile Disposition disposition;
    /** Why the client asked not to be called again (§11.4); null unless they did. */
    private volatile String doNotCallReason;

    void recordOutcome(String key, Object value) {
        if (value != null) {
            outcome.put(key, value);
        }
    }

    Map<String, Object> outcome() {
        return Map.copyOf(outcome);
    }

    Disposition disposition() {
        return disposition;
    }

    void setDisposition(Disposition disposition) {
        this.disposition = disposition;
    }

    String doNotCallReason() {
        return doNotCallReason;
    }

    void setDoNotCallReason(String doNotCallReason) {
        this.doNotCallReason = doNotCallReason;
    }

    void recordToolFigures(Collection<BigDecimal> figures) {
        toolFigures.addAll(figures);
    }

    Set<BigDecimal> toolFigures() {
        return Collections.unmodifiableSet(toolFigures);
    }

    int countToolCall(String toolName) {
        return toolCallCounts.computeIfAbsent(toolName, name -> new AtomicInteger()).incrementAndGet();
    }

    PendingToolConfirmation findPendingConfirmation(String toolName) {
        return pendingConfirmations.get(toolName);
    }

    void setPendingConfirmation(String toolName, PendingToolConfirmation pending) {
        if (pending == null) {
            pendingConfirmations.remove(toolName);
        } else {
            pendingConfirmations.put(toolName, pending);
        }
    }
}
