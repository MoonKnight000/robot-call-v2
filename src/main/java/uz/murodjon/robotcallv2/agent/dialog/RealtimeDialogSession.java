package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.tool.ToolCallback;

import uz.murodjon.robotcallv2.agent.realtime.RealtimeSession;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * State for one call running on a speech-to-speech engine — the realtime counterpart to
 * {@link DialogSession}.
 *
 * <p>Much smaller than that class, and deliberately so: message history, prompt caching,
 * token accounting, turn latency, filler lines, interrupted/unspoken text and the
 * no-input watchdog are all bookkeeping the cascade pipeline needs because <em>it</em>
 * runs the conversation. Here the engine runs it. What is left is what the platform still
 * owns regardless of who does the talking: the FSM state, the outcome the call produced,
 * and the transcript.
 */
public class RealtimeDialogSession implements DialogOutcomeSink {

    private final String channelId;
    private final String language;
    private final long callAttemptId;
    private final ScenarioDefinition scenario;
    private final CallContext context;
    private final RtpEndpoint endpoint;
    private final Runnable hangup;
    private final Runnable transfer;
    private final Instant startedAt = Instant.now();

    /** {@code ROLE: text} lines, for the operator handover screen (§11.6). */
    private final List<String> transcript = new ArrayList<>();
    private final Map<String, Object> outcome = new ConcurrentHashMap<>();
    private final AtomicInteger factViolations = new AtomicInteger();

    /**
     * The tools declared to the engine, built once. Kept because a tool call names the
     * tool it wants and the callback has to be found again — rebuilding the list per call
     * would also rebuild every callback's binding to this session.
     */
    private volatile List<ToolCallback> tools = List.of();

    /** Set once the engine is connected; null while the session is still being opened. */
    private volatile RealtimeSession engine;
    private volatile String state;
    private volatile Disposition disposition;
    private volatile String doNotCallReason;
    /** Puts keypad tones on this call (IVR navigation); null when the call has no channel. */
    private volatile Consumer<String> dtmfSender;
    private volatile boolean ended;
    /** Turns the engine completed — the closest realtime equivalent of an LLM turn count. */
    private volatile int turnCount;

    public RealtimeDialogSession(String channelId, String language, long callAttemptId,
                                 ScenarioDefinition scenario, CallContext context, RtpEndpoint endpoint,
                                 Runnable hangup, Runnable transfer) {
        this.channelId = channelId;
        this.language = language;
        this.callAttemptId = callAttemptId;
        this.scenario = scenario;
        this.context = context;
        this.endpoint = endpoint;
        this.hangup = hangup;
        this.transfer = transfer;
        this.state = scenario.stages().get(0).id();
    }

    @Override
    public String channelId() {
        return channelId;
    }

    /**
     * No-op: the engine speaks its line as audio, and by the time a tool call reaches us
     * the caller has already heard it. Only the cascade pipeline needs the line back.
     */
    @Override
    public void addToolReply(String reply) {
    }

    @Override
    public void setState(String state) {
        this.state = state;
    }

    @Override
    public void recordOutcome(String key, Object value) {
        if (value != null) {
            outcome.put(key, value);
        }
    }

    @Override
    public void setDisposition(Disposition disposition) {
        this.disposition = disposition;
    }

    @Override
    public void end(Disposition disposition) {
        this.disposition = disposition;
        this.ended = true;
    }

    @Override
    public void setDoNotCallReason(String reason) {
        this.doNotCallReason = reason;
    }

    /** See {@link DialogSession#setDtmfSender} — same wiring, speech-to-speech engine. */
    public void setDtmfSender(Consumer<String> sender) {
        this.dtmfSender = sender;
    }

    @Override
    public boolean sendDtmf(String digits) {
        Consumer<String> sender = dtmfSender;
        if (sender == null) {
            return false;
        }
        sender.accept(digits);
        return true;
    }

    public String language() {
        return language;
    }

    public long callAttemptId() {
        return callAttemptId;
    }

    public ScenarioDefinition scenario() {
        return scenario;
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

    public Runnable transfer() {
        return transfer;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public String state() {
        return state;
    }

    public Disposition disposition() {
        return disposition;
    }

    public String doNotCallReason() {
        return doNotCallReason;
    }

    public Map<String, Object> outcome() {
        return outcome;
    }

    public boolean isEnded() {
        return ended;
    }

    public List<ToolCallback> tools() {
        return tools;
    }

    public void setTools(List<ToolCallback> tools) {
        this.tools = tools;
    }

    public RealtimeSession engine() {
        return engine;
    }

    public void setEngine(RealtimeSession engine) {
        this.engine = engine;
    }

    public int turnCount() {
        return turnCount;
    }

    public void countTurn() {
        turnCount++;
    }

    public int recordFactViolation() {
        return factViolations.incrementAndGet();
    }

    public int factViolations() {
        return factViolations.get();
    }

    /** Append one line to the operator handover transcript. */
    public synchronized void addTranscriptLine(String role, String text) {
        transcript.add(role + ": " + text);
    }

    public synchronized String transcriptText() {
        return String.join("\n", transcript);
    }
}
