package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeSession;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentWebhookEvent;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * State for one call running on a speech-to-speech engine — the realtime counterpart to
 * {@link DialogSession}.
 *
 * <p>Much smaller than that class, and deliberately so: message history, prompt caching,
 * filler lines and speculation are all bookkeeping the cascade pipeline needs because
 * <em>it</em> runs the conversation. Here the engine runs it. What is left is what the
 * platform still owns regardless of who does the talking: the FSM state, the outcome the
 * call produced, the transcript — and the measurements that let a realtime call be
 * compared with a cascade one (tokens, turn latency), the silence watchdog, and the
 * playback bookkeeping that decides how much of an interrupted reply was heard.
 */
public class RealtimeDialogSession implements DialogOutcomeSink {

    /**
     * How long after the caller stops talking the engine is presumed to be composing an
     * answer. Past this an unanswered utterance no longer holds off the silence watchdog,
     * and the eventual audio is no longer counted as that utterance's turnaround.
     */
    private static final Duration MAX_TURNAROUND = Duration.ofSeconds(10);

    private final String channelId;
    private final String language;
    private final long callAttemptId;
    private final long companyId;
    /** The agent behind this call; null on a manual test line that names none. */
    private final AiAgent agent;
    /** Replaced only by an agent handoff ({@link #handOffToAgent}). */
    private volatile ScenarioDefinition scenario;
    /** Moves the call to another agent's flow and reconfigures the engine; null when the call cannot. */
    private volatile LongPredicate agentHandoff;
    private final CallContext context;
    private final RtpEndpoint endpoint;
    private final Runnable hangup;
    private final Runnable transfer;
    private final Instant startedAt = Instant.now();

    /** {@code ROLE: text} lines, for the operator handover screen (§11.6). */
    private final List<String> transcript = new ArrayList<>();
    private final CallOutcomeLedger outcomeLedger = new CallOutcomeLedger();
    /** Tokens, turn latency and fact violations — the same ledger the cascade session keeps. */
    private final UsageLedger usageLedger = new UsageLedger();

    /** Tool calls the engine is waiting on right now — an answer is still coming. */
    private final AtomicInteger toolsInFlight = new AtomicInteger();
    /** The engine says it is still reasoning or waiting on a tool — an answer is still coming. */
    private volatile boolean engineWorking;
    /** Set by whoever starts hanging the call up, so it happens once. */
    private final AtomicBoolean closing = new AtomicBoolean();

    /**
     * The tools declared to the engine, built once. Kept because a tool call names the
     * tool it wants and the callback has to be found again — rebuilding the list per call
     * would also rebuild every callback's binding to this session.
     */
    private volatile List<ToolCallback> tools = List.of();

    /** Set once the engine is connected; null while the session is still being opened. */
    private volatile RealtimeSession engine;
    /** The caller's side of the line; read at the end for how much echo it withheld. */
    private volatile RealtimeAudioBridge audioBridge;
    /**
     * HALF_CASCADE: our STT hears the caller and the engine is handed the text, so it never
     * gets the caller's audio and cannot notice a barge-in by itself.
     */
    private volatile boolean sttDriven;
    /**
     * HALF_CASCADE: the caller cut in over a reply the engine is still generating; the rest
     * of that reply's audio is dropped until the engine finishes or is interrupted.
     */
    private volatile boolean discardingReply;
    /** What the caller last heard the engine say — a caller line inside it is the bot's echo. */
    private volatile String lastAgentText = "";
    private volatile String state;
    private volatile BiConsumer<AgentWebhookEvent, Map<String, Object>> webhookEventPublisher;
    private final AtomicBoolean voicemailEventPublished = new AtomicBoolean();
    private volatile CustomerSentiment lastCustomerSentiment = CustomerSentiment.NEUTRAL;
    /** Puts keypad tones on this call (IVR navigation); null when the call has no channel. */
    private volatile Consumer<String> dtmfSender;
    /** Set when the agent allows a mid-call SMS ({@link MidCallSmsSender}); null otherwise. */
    private volatile Predicate<String> midCallSmsSender;
    private volatile boolean ended;
    /** Turns the engine completed — the closest realtime equivalent of an LLM turn count. */
    private volatile int turnCount;
    /** Of {@link #turnCount}, the ones in which the caller actually heard the engine speak. */
    private volatile int spokenTurnCount;
    /**
     * {@link #turnCount} when the FSM stepped into {@link #state}. Starts at zero for the
     * opening stage, which the call begins in rather than transitions into.
     */
    private volatile int stageEnteredTurn;
    /** What the call actually ran on, recorded at connect — see {@link #setEngineIdentity}. */
    private volatile String engineName;
    private volatile String llmModel;
    private volatile String ttsVoice;

    /** {@code System.nanoTime()} the opening line was requested; 0 once its audio was measured. */
    private volatile long openingRequestedAtNanos;
    /**
     * {@code System.nanoTime()} the caller finished an utterance on a free line, or 0 while
     * no answer is owed. Measured by our own VAD, so every engine is timed the same way.
     */
    private volatile long callerStoppedAtNanos;
    /**
     * Sample of the endpoint's playback stream at which the latest reply began, or -1 when
     * there is none to measure against. Outlives the engine finishing the reply: the engine
     * generates faster than the line plays, so the caller can still cut into a reply the
     * engine considers done.
     */
    private volatile long replyStartSample = -1;
    /** Whether the engine is still producing the current reply; its next audio belongs to it. */
    private volatile boolean replyOpen;
    /** Share of the reply just interrupted that the caller heard, until its transcript arrives. */
    private volatile Double heardShare;
    /** A supervisor's whisper that came in while a reply was being generated; sent once it ends. */
    private final AtomicReference<String> heldWhisper = new AtomicReference<>();

    private volatile NoInputWatchdog watchdog;
    private volatile int maxCallSeconds;
    /** Prompt + completion tokens after which the call is closed (§C14); 0 for no limit. */
    private volatile long maxTokensPerCall;
    private volatile ScheduledFuture<?> watchdogTask;

    /**
     * The §11.1 line the engine was told to open with, or null when none was required;
     * cleared once it has been confirmed spoken or re-requested.
     */
    private volatile String pendingDisclosure;
    /** Whether this call was received as inbound rather than dialled outbound. */
    private volatile boolean inbound;
    /** What the engine said before the disclosure was checked. Guarded by {@code this}. */
    private final StringBuilder openingSpeech = new StringBuilder();

    public RealtimeDialogSession(String channelId, String language, long callAttemptId, long companyId,
                                 AiAgent agent, ScenarioDefinition scenario, CallContext context,
                                 RtpEndpoint endpoint, Runnable hangup, Runnable transfer) {
        this.channelId = channelId;
        this.language = language;
        this.callAttemptId = callAttemptId;
        this.companyId = companyId;
        this.agent = agent;
        this.scenario = scenario;
        this.context = context;
        this.endpoint = endpoint;
        this.hangup = hangup;
        this.transfer = transfer;
        this.state = scenario.findStartStage().id();
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
        String previousState = this.state;
        if (!Objects.equals(this.state, state)) {
            this.stageEnteredTurn = turnCount;
        }
        this.state = state;
        if (previousState != null && !Objects.equals(previousState, state)) {
            publishWebhookEvent(AgentWebhookEvent.STAGE_TRANSITION,
                    Map.of("fromStage", previousState, "toStage", state == null ? "" : state));
        }
    }

    /**
     * Whether the FSM stepped into the current stage on the turn that is running right
     * now — so this stage's own line has not been spoken yet, and the caller has had no
     * chance to answer it.
     *
     * <p>What it is for: the engine calls its tools in one batch, and a batch that both
     * enters a stage and records that stage's outcome has produced an outcome out of
     * nothing (see {@code RealtimeToolRunner#refuse}).
     */
    public boolean stageJustEntered() {
        return stageEnteredTurn == turnCount;
    }

    @Override
    public void recordOutcome(String key, Object value) {
        outcomeLedger.recordOutcome(key, value);
        if (key != null && value != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("key", key);
            if (value instanceof String text) {
                data.put("value", PiiRedactor.redact(text));
            } else if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
                data.put("value", value);
            }
            publishWebhookEvent(AgentWebhookEvent.FACT_EXTRACTED, data);
        }
    }

    @Override
    public void setDisposition(Disposition disposition) {
        outcomeLedger.setDisposition(disposition);
        publishVoicemailEvent(disposition);
    }

    @Override
    public void end(Disposition disposition) {
        outcomeLedger.setDisposition(disposition);
        publishVoicemailEvent(disposition);
        this.ended = true;
    }

    @Override
    public void setDoNotCallReason(String reason) {
        outcomeLedger.setDoNotCallReason(reason);
    }

    public String transferReason() {
        return outcomeLedger.transferReason();
    }

    @Override
    public void setTransferReason(String reason) {
        outcomeLedger.setTransferReason(reason);
        if (reason != null && !reason.isBlank()) {
            publishWebhookEvent(AgentWebhookEvent.TRANSFER_REQUESTED,
                    Map.of("reason", PiiRedactor.redact(reason)));
        }
    }

    /** See {@link DialogSession#setDtmfSender} — same wiring, speech-to-speech engine. */
    public void setMidCallSmsSender(Predicate<String> midCallSmsSender) {
        this.midCallSmsSender = midCallSmsSender;
    }

    public boolean isInbound() {
        return inbound;
    }

    public void setInbound(boolean inbound) {
        this.inbound = inbound;
    }

    @Override
    public boolean triggerMidCallSms(String text) {
        Predicate<String> sender = midCallSmsSender;
        return sender != null && text != null && !text.isBlank() && sender.test(text.trim());
    }

    public void setAgentHandoff(LongPredicate agentHandoff) {
        this.agentHandoff = agentHandoff;
    }

    /**
     * Only where the engine takes new instructions and tools mid-call ({@code
     * RealtimeSession#updateConfiguration}); one configured once at setup keeps its scenario.
     */
    @Override
    public boolean handOffToAgent(long agentId) {
        LongPredicate handoff = agentHandoff;
        return handoff != null && handoff.test(agentId);
    }

    /** Continue under {@code next} from its start stage; what was said stays in the engine's history. */
    void switchScenario(ScenarioDefinition next) {
        this.scenario = next;
        setState(next.findStartStage().id());
    }

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

    /** No-op: the engine does its own turn-taking and is already listening. */
    @Override
    public void waitForCaller() {
    }

    /** Always {@code false}: the engine's language is set when it connects and is not switched mid-call. */
    @Override
    public boolean switchCallLanguage(String language) {
        return false;
    }

    @Override
    public void noteCallerMood(CustomerSentiment mood) {
        CustomerSentiment next = mood != null ? mood : CustomerSentiment.NEUTRAL;
        CustomerSentiment previous = lastCustomerSentiment;
        lastCustomerSentiment = next;
        if (previous != next) {
            publishWebhookEvent(AgentWebhookEvent.SENTIMENT_CHANGE, Map.of("sentiment", next.name()));
        }
    }

    /** No-op: the engine keeps its own tool results in its context. */
    @Override
    public void rememberCatalogResult(String result) {
    }

    /*
     * The four below serve only the write-tool confirmation in HttpToolExecutor, and company
     * HTTP tools are offered on cascade calls only (TurnTools). Failing loudly here is the
     * point: answering "nothing pending, caller said nothing" would hold every write tool
     * for a yes that could never be counted.
     */

    @Override
    public PendingToolConfirmation findPendingConfirmation(String toolName) {
        throw new IllegalStateException("company HTTP tools do not run on realtime calls");
    }

    @Override
    public void setPendingConfirmation(String toolName, PendingToolConfirmation pending) {
        throw new IllegalStateException("company HTTP tools do not run on realtime calls");
    }

    @Override
    public int countCallerUtterances() {
        throw new IllegalStateException("company HTTP tools do not run on realtime calls");
    }

    @Override
    public String findLastCallerText() {
        throw new IllegalStateException("company HTTP tools do not run on realtime calls");
    }

    public String language() {
        return language;
    }

    public long callAttemptId() {
        return callAttemptId;
    }

    public long companyId() {
        return companyId;
    }

    public AiAgent agent() {
        return agent;
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
        return outcomeLedger.disposition();
    }

    public String doNotCallReason() {
        return outcomeLedger.doNotCallReason();
    }

    @Override
    public Map<String, Object> outcome() {
        return outcomeLedger.outcome();
    }

    @Override
    public void recordToolFigures(Collection<BigDecimal> figures) {
        outcomeLedger.recordToolFigures(figures);
    }

    /** Every figure a company tool has answered with so far this call; never null. */
    public Set<BigDecimal> toolFigures() {
        return outcomeLedger.toolFigures();
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

    public RealtimeAudioBridge audioBridge() {
        return audioBridge;
    }

    public void setAudioBridge(RealtimeAudioBridge audioBridge) {
        this.audioBridge = audioBridge;
    }

    boolean isSttDriven() {
        return sttDriven;
    }

    void setSttDriven(boolean sttDriven) {
        this.sttDriven = sttDriven;
    }

    boolean isDiscardingReply() {
        return discardingReply;
    }

    void setDiscardingReply(boolean discardingReply) {
        this.discardingReply = discardingReply;
    }

    String lastAgentText() {
        return lastAgentText;
    }

    void setLastAgentText(String lastAgentText) {
        this.lastAgentText = lastAgentText;
    }

    /**
     * What this call is running on, for the "Texnik" tab (§10.5): the engine's id, the
     * model it was opened with, and the catalog voice it accepted — {@code null} when the
     * engine kept its own default voice, so the tab does not name a voice nobody heard.
     */
    public void setEngineIdentity(String engineName, String llmModel, String ttsVoice) {
        this.engineName = engineName;
        this.llmModel = llmModel;
        this.ttsVoice = ttsVoice;
    }

    public String engineName() {
        return engineName;
    }

    public String llmModel() {
        return llmModel;
    }

    public String ttsVoice() {
        return ttsVoice;
    }

    public int turnCount() {
        return turnCount;
    }

    public void countTurn() {
        turnCount++;
    }

    public int spokenTurnCount() {
        return spokenTurnCount;
    }

    public void countSpokenTurn() {
        spokenTurnCount++;
    }

    public void recordBargeIn() {
        usageLedger.recordBargeIn();
    }

    public int bargeIns() {
        return usageLedger.bargeIns();
    }

    public int recordFactViolation() {
        return usageLedger.recordFactViolation();
    }

    public int factViolations() {
        return usageLedger.factViolations();
    }

    /** Add one engine response's reported usage; a negative count adds nothing. */
    public void addUsage(long prompt, long completion, long cached) {
        usageLedger.addTokenBreakdown(prompt, completion, cached);
    }

    public long promptTokens() {
        return usageLedger.promptTokens();
    }

    public long completionTokens() {
        return usageLedger.completionTokens();
    }

    public long cachedTokens() {
        return usageLedger.cachedTokens();
    }

    /** The opening line has just been asked for; its first audio is timed from now. */
    public void markOpeningRequested() {
        openingRequestedAtNanos = System.nanoTime();
    }

    /**
     * How long the opening line took to start playing, once — null on every later call,
     * and null if it was never requested.
     */
    public Duration takeOpeningLatency(long nowNanos) {
        long requested = openingRequestedAtNanos;
        if (requested == 0) {
            return null;
        }
        openingRequestedAtNanos = 0;
        return Duration.ofNanos(nowNanos - requested);
    }

    /** The caller finished an utterance with the line free: an answer is owed from now. */
    public void markCallerStopped(long atNanos) {
        callerStoppedAtNanos = atNanos;
    }

    /** The caller is talking again, so whatever was owed is owed from a later moment. */
    public void clearCallerStopped() {
        callerStoppedAtNanos = 0;
    }

    /**
     * The wait the engine's answer just ended, in ms, once — or null when no answer was
     * owed, or when it has been owed for so long that this audio is not really its answer.
     */
    public Long takeTurnaroundMs(long nowNanos) {
        long stopped = callerStoppedAtNanos;
        if (stopped == 0) {
            return null;
        }
        callerStoppedAtNanos = 0;
        long elapsed = nowNanos - stopped;
        if (elapsed < 0 || elapsed > MAX_TURNAROUND.toNanos()) {
            return null;
        }
        return elapsed / 1_000_000;
    }

    /** Whether the caller finished recently enough that the engine is presumably still answering. */
    public boolean awaitingAnswer(long nowNanos) {
        long stopped = callerStoppedAtNanos;
        return stopped != 0 && nowNanos - stopped < MAX_TURNAROUND.toNanos();
    }

    public void recordTurnLatency(long ms) {
        usageLedger.recordTurnLatency(ms);
    }

    public Integer avgTurnLatencyMs() {
        return usageLedger.avgTurnLatencyMs();
    }

    public Integer maxTurnLatencyMs() {
        return usageLedger.maxTurnLatencyMs();
    }

    public long replyStartSample() {
        return replyStartSample;
    }

    public boolean replyOpen() {
        return replyOpen;
    }

    /** The engine began a reply whose first audio goes out at {@code sample} of the playback stream. */
    public void startReply(long sample) {
        replyStartSample = sample;
        replyOpen = true;
        heardShare = null;
    }

    /** The engine finished producing the reply; it may still be playing. */
    public void closeReply() {
        replyOpen = false;
    }

    /** The reply is gone — cut off, or superseded. */
    public void endReply() {
        replyStartSample = -1;
        replyOpen = false;
    }

    public void setHeardShare(Double share) {
        heardShare = share;
    }

    /** Keep a supervisor's whisper until the reply being generated ends; a newer one replaces it. */
    public void holdWhisper(String instruction) {
        heldWhisper.set(instruction);
    }

    /** The held whisper, once; null when none is waiting. */
    public String takeHeldWhisper() {
        return heldWhisper.getAndSet(null);
    }

    /** The share of the interrupted reply the caller heard, once; null when nothing was cut off. */
    public Double takeHeardShare() {
        Double share = heardShare;
        heardShare = null;
        return share;
    }

    public AtomicInteger toolsInFlight() {
        return toolsInFlight;
    }

    public boolean isEngineWorking() {
        return engineWorking;
    }

    public void setEngineWorking(boolean engineWorking) {
        this.engineWorking = engineWorking;
    }

    /** Claim the hang-up; true for exactly one caller. */
    public boolean beginClosing() {
        return closing.compareAndSet(false, true);
    }

    public NoInputWatchdog watchdog() {
        return watchdog;
    }

    public void setWatchdog(NoInputWatchdog watchdog) {
        this.watchdog = watchdog;
    }

    /** Seconds after which the call is closed ({@code CallLimits}); 0 for no limit. */
    public int maxCallSeconds() {
        return maxCallSeconds;
    }

    public void setMaxCallSeconds(int maxCallSeconds) {
        this.maxCallSeconds = maxCallSeconds;
    }

    public long maxTokensPerCall() {
        return maxTokensPerCall;
    }

    public void setMaxTokensPerCall(long maxTokensPerCall) {
        this.maxTokensPerCall = maxTokensPerCall;
    }

    @Override
    public int countToolCall(String toolName) {
        return outcomeLedger.countToolCall(toolName);
    }

    /** What the budget counts: the same prompt + completion sum the cascade pipeline uses. */
    public long tokensUsed() {
        return usageLedger.promptTokens() + usageLedger.completionTokens();
    }

    public ScheduledFuture<?> watchdogTask() {
        return watchdogTask;
    }

    public void setWatchdogTask(ScheduledFuture<?> watchdogTask) {
        this.watchdogTask = watchdogTask;
    }

    /** Record activity on the line for the silence watchdog; a no-op when it is off. */
    public void touch() {
        NoInputWatchdog current = watchdog;
        if (current != null) {
            current.touch(Instant.now());
        }
    }

    public void setPendingDisclosure(String disclosure) {
        this.pendingDisclosure = disclosure == null || disclosure.isBlank() ? null : disclosure;
    }

    /** The disclosure still waiting to be confirmed, or null when there is none or it was settled. */
    public String pendingDisclosure() {
        return pendingDisclosure;
    }

    /** Settle the disclosure check: it happens once per call, whatever it found. */
    public void clearPendingDisclosure() {
        pendingDisclosure = null;
    }

    public synchronized void appendOpeningSpeech(String text) {
        if (pendingDisclosure == null || text == null || text.isBlank()) {
            return;
        }
        if (!openingSpeech.isEmpty()) {
            openingSpeech.append(' ');
        }
        openingSpeech.append(text.trim());
    }

    public synchronized String openingSpeech() {
        return openingSpeech.toString();
    }

    /** Append one line to the operator handover transcript. */
    public synchronized void addTranscriptLine(String role, String text) {
        transcript.add(role + ": " + text);
    }

    public synchronized String transcriptText() {
        return String.join("\n", transcript);
    }

    public void setWebhookEventPublisher(BiConsumer<AgentWebhookEvent, Map<String, Object>> publisher) {
        this.webhookEventPublisher = publisher;
    }

    @Override
    public boolean isWebhookEventEnabled(AgentWebhookEvent event) {
        return event != null && agent != null && agent.postCallWebhook() != null
                && agent.postCallWebhook().events().contains(event) && webhookEventPublisher != null;
    }

    @Override
    public void publishWebhookEvent(AgentWebhookEvent event, Map<String, Object> data) {
        BiConsumer<AgentWebhookEvent, Map<String, Object>> publisher = webhookEventPublisher;
        if (publisher == null || !isWebhookEventEnabled(event)) {
            return;
        }
        Map<String, Object> safeData = new HashMap<>();
        if (data != null) {
            data.forEach((key, value) -> {
                if (value instanceof String text) {
                    safeData.put(key, PiiRedactor.redact(text));
                } else if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
                    safeData.put(key, value);
                }
            });
        }
        publisher.accept(event, Map.copyOf(safeData));
    }

    private void publishVoicemailEvent(Disposition disposition) {
        if (disposition == Disposition.VOICEMAIL && voicemailEventPublished.compareAndSet(false, true)) {
            publishWebhookEvent(AgentWebhookEvent.VOICEMAIL_DETECTED, Map.of());
        }
    }
}
