package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.agent.metrics.TurnLatency;
import uz.murodjon.robotcallv2.agent.metrics.TurnTiming;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.PronunciationRule;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Mutable in-memory state for one call's conversation (PROJECT.md §4, §5.1). Holds
 * the FSM state, the chat history sent to the LLM each turn, guardrail counters, and
 * the outputs a turn's tool calls record.
 *
 * <p>The per-turn machinery lives in three parts this class delegates to:
 * {@link TurnState} (the busy claim, barge-in, deferred input, speculation),
 * {@link SpokenAudioLedger} (what the caller actually heard of this turn) and
 * {@link UsageLedger} (tokens, characters, latency). Each documents its own
 * thread-safety.
 *
 * <p>Thread-safety of what is left here: every mutable field is volatile, atomic or a
 * concurrent collection, because it is written on the turn, tool and watchdog threads and
 * read on the ARI lane at teardown ({@link #disposition()} among them). Compound steps
 * — a turn's reads and writes of the FSM state, the history and the language candidate —
 * are not locked; {@code busy} serializes the turns that make them.
 */
public class DialogSession implements DialogOutcomeSink {

    private final String channelId;
    /**
     * The language this call is being spoken in. Starts as the one the target was dialled
     * with and changes if the caller turns out to speak the other one
     * ({@link #switchLanguage}) — the campaign is bilingual, the caller is not.
     */
    private volatile String language;
    /**
     * The languages the caller may turn out to speak — the company's own list, falling
     * back to the deployment's {@code stt.detect-languages} when it set none.
     */
    private volatile List<String> detectLanguages = List.of();
    /** Catalog id of the voice speaking this call (§2.5); null = configured routing. */
    private volatile String ttsVoice;
    /** The campaign's voice per language, used when the call switches language. */
    private final Map<String, String> languageVoices;
    /** Silence watchdog for this call; null when the watchdog is disabled. */
    private final NoInputWatchdog watchdog;
    private final CallContext context;
    /**
     * The scenario this call runs (ROADMAP A.3) — resolved at {@code startCall}, replaced
     * only by a mid-call handoff ({@link #switchScenario}).
     */
    private volatile ScenarioDefinition scenario;
    private final RtpEndpoint endpoint;
    private final Runnable hangup;
    private final Runnable transfer;
    /** Puts keypad tones on this call (IVR navigation); null when the call has no channel. */
    private volatile Consumer<String> dtmfSender;
    private final long callAttemptId;
    /** The company this call's campaign belongs to — scopes the per-company lookups a turn makes. */
    private final long companyId;
    /** This call's company's AI-model overrides merged over the process defaults (§11 settings), resolved once. */
    private final EffectiveAiModelConfig aiModel;
    /**
     * The model this agent answers a very short caller turn on, or null for the
     * installation's {@code dialog.fast-model}.
     *
     * <p>Set from the agent rather than carried in {@link EffectiveAiModelConfig}: that
     * record is shared with the realtime pipeline, which has no per-turn model choice to
     * make, and putting a cascade-only field in it would mean a null nobody reads on
     * every realtime call. Written once at startup, before any turn runs.
     */
    private volatile String fastModel;
    /**
     * The client on the company's own LLM ({@code EffectiveAiModelConfig#ownLlm}), or null on the
     * platform's. Built once at startup, before any turn runs.
     */
    private volatile ChatClient ownChatClient;
    /** This call's company's TTS overrides (§11 settings/voice), resolved once. */
    private final EffectiveVoiceSettings voiceSettings;
    private final boolean emotionAdaptiveVoice;
    private volatile CustomerSentiment lastCustomerSentiment = CustomerSentiment.NEUTRAL;
    /** Campaign's own choice on the §11.1 opening disclosure (§10.6); the engine also
     * checks the global {@code mandatory-disclosure} kill-switch on top of this. */
    private final boolean disclosureEnabled;
    private final AgentPersona agentPersona;
    private final String firstMessage;
    private final List<Tool> tools;
    /** Canned tool answers of a simulation; null on a real call (DialogSessionBuilder#toolMocks). */
    private final Map<String, String> toolMocks;
    /** Tools a simulation answered from {@link #toolMocks}, in order, until the simulator drains them. */
    private final List<String> simulatedToolCalls = new CopyOnWriteArrayList<>();
    private final List<PronunciationRule> pronunciationRules;
    private final AiAgent agent;
    private volatile Predicate<String> midCallSmsSender;

    /** Whose name the §11.1 disclosure is spoken in — the company this call's campaign belongs to. */
    private final String companyName;
    /** That company's own wording of the §11.1 disclosure (company config), resolved once. */
    private final String companyDisclosureText;
    /**
     * Copy-on-write: the greeting appends to it without holding {@code busy}, and a
     * speculation copies it from another thread while a turn may be adding to it.
     */
    private final List<Message> history = new CopyOnWriteArrayList<>();
    private final Instant startedAt = Instant.now();

    /** Current stage id — a scenario StageDef.id(), not an enum (ROADMAP A.3). */
    private volatile String state;
    private volatile boolean ended = false;
    /**
     * The caller's line is gone — hung up or torn down. Unlike {@link #ended}, which a
     * farewell tool also sets while its goodbye is still to be spoken and written down,
     * nothing more is spoken, run or written for the call once this is set.
     */
    private volatile boolean lineGone = false;
    /** Whether this call was received as inbound rather than dialled outbound. */
    private volatile boolean inbound;
    /** Set by whoever starts hanging the call up ({@code SpeechOutput#finishWhenSpoken}), so it happens once. */
    private final AtomicBoolean closing = new AtomicBoolean();
    private volatile String lastAgentText;
    /**
     * The unchanging half of the prompt, built once on the first turn and then reused
     * byte for byte. Rebuilding it per turn would produce the same text at best, and a
     * different one across midnight — either way the provider's implicit context cache
     * keys on this exact prefix, so it is stored rather than regenerated.
     */
    private volatile String systemPrefix;
    /** The supervisor's latest guidance for this call (LiveCallControlHub), or null; the caller never hears it. */
    private volatile String operatorWhisper;
    /**
     * The language the caller has been answering in while the call is still being held
     * in another one, and how many finals in a row said so. Two are required before the
     * call turns over: one sentence can be misrecognized, and switching on it would
     * answer an Uzbek caller in Russian.
     */
    private volatile String candidateLanguage;
    private volatile int candidateLanguageTurns;
    /** Whether the §11.1 disclosure has already been spoken from code this call. */
    private volatile boolean disclosureSpoken;

    /**
     * The MCP tool callbacks for this call, resolved once.
     *
     * <p>Cached on the session because building them reads the company's connections, and
     * the tool list is re-sent on every turn — a query per turn would put a database round
     * trip inside the pause the caller hears.
     */
    private volatile List<ToolCallback> mcpTools;

    /** Whether this call's agent has a catalog to search; resolved on the first turn for the same reason as {@link #mcpTools}. */
    private volatile Boolean catalogAvailable;
    /** The last catalog search's answer ({@link CatalogTools}), shown again in every turn's annex until the next one. */
    private volatile String catalogResult;

    private final Map<String, Integer> stageAttempts = new ConcurrentHashMap<>();
    /** {@link #markBargeInPending}'s token, 0 when no barge-in is waiting to be confirmed. */
    private final AtomicLong bargeInPendingSince = new AtomicLong();
    /** Set by the waitForCaller tool for the turn in progress; cleared when the next turn starts. */
    private volatile boolean waitingForCaller;

    // Recorded by tool calls. disposition is read at teardown (CampaignOutcomeService.applyOutcome,
    // CallFinalizer) on the ARI lane, while tools, turns and the watchdog write it on theirs.
    // outcome feeds the live view (operatorSnapshot) and settles whether the scenario's goal
    // was reached (GoalOutcome); the persisted call_result.outcome still comes from
    // SummaryService's independent post-call pass over the transcript (ROADMAP A.3).
    private final CallOutcomeLedger outcomeLedger = new CallOutcomeLedger();
    /** {@link #turnCount} when the first outcome was recorded; -1 until one is. */
    private volatile int outcomeTurn = -1;

    /** Cancels the silence watchdog when the call ends; null when it is disabled. */
    private volatile ScheduledFuture<?> watchdogTask;

    private final TurnState turnState;
    private final SpokenAudioLedger spokenAudioLedger;
    private final UsageLedger usageLedger;

    /** Starts a session for {@code channelId} spoken in {@code language}; everything else is optional. */
    public static DialogSessionBuilder builder(String channelId, String language) {
        return new DialogSessionBuilder(channelId, language);
    }

    DialogSession(DialogSessionBuilder builder) {
        this.channelId = builder.channelId;
        this.language = builder.language;
        this.ttsVoice = builder.ttsVoice;
        this.languageVoices = builder.languageVoices == null ? Map.of() : Map.copyOf(builder.languageVoices);
        this.context = builder.context;
        this.scenario = builder.scenario;
        // The scenario's first declared stage is its entry point, by convention
        // (ROADMAP A.3 — every builtin template already lists its opening stage first).
        this.state = (scenario != null && scenario.stages() != null && !scenario.stages().isEmpty())
                ? scenario.stages().get(0).id()
                : "CONVERSATION";
        this.endpoint = builder.endpoint;
        this.hangup = builder.hangup;
        this.transfer = builder.transfer;
        this.callAttemptId = builder.callAttemptId;
        this.companyId = builder.companyId;
        this.watchdog = builder.watchdog;
        this.disclosureEnabled = builder.disclosureEnabled;
        this.agentPersona = builder.agentPersona != null ? builder.agentPersona : AgentPersona.AI_ASSISTANT;
        this.companyName = builder.companyName;
        this.companyDisclosureText = builder.companyDisclosureText;
        this.aiModel = builder.aiModel;
        this.voiceSettings = builder.voiceSettings;
        this.emotionAdaptiveVoice = builder.emotionAdaptiveVoice;
        this.firstMessage = builder.firstMessage;
        this.tools = builder.tools == null ? List.of() : List.copyOf(builder.tools);
        this.toolMocks = builder.toolMocks == null ? null : Map.copyOf(builder.toolMocks);
        this.pronunciationRules = builder.pronunciationRules == null ? List.of() : List.copyOf(builder.pronunciationRules);
        this.agent = builder.agent;
        this.turnState = new TurnState();
        this.spokenAudioLedger = new SpokenAudioLedger(endpoint);
        this.usageLedger = new UsageLedger();
    }

    // ------------------------------------------------------------------
    // Identity and per-call configuration
    // ------------------------------------------------------------------

    public List<PronunciationRule> pronunciationRules() {
        return pronunciationRules != null ? pronunciationRules : List.of();
    }

    public AiAgent agent() {
        return agent;
    }

    public void setMidCallSmsSender(Predicate<String> midCallSmsSender) {
        this.midCallSmsSender = midCallSmsSender;
    }

    @Override
    public boolean triggerMidCallSms(String text) {
        Predicate<String> sender = midCallSmsSender;
        return sender != null && text != null && !text.isBlank() && sender.test(text.trim());
    }

    public String firstMessage() {
        return firstMessage;
    }

    public boolean isInbound() {
        return inbound;
    }

    public void setInbound(boolean inbound) {
        this.inbound = inbound;
    }

    public List<ToolCallback> mcpTools() {
        return mcpTools;
    }

    public void setMcpTools(List<ToolCallback> mcpTools) {
        this.mcpTools = mcpTools;
    }

    /** Null until the first turn has asked. */
    public Boolean catalogAvailable() {
        return catalogAvailable;
    }

    public void setCatalogAvailable(boolean catalogAvailable) {
        this.catalogAvailable = catalogAvailable;
    }

    @Override
    public void rememberCatalogResult(String result) {
        this.catalogResult = result;
    }

    /** The last catalog search's answer, or null before the first one. */
    public String catalogResult() {
        return catalogResult;
    }

    public List<Tool> tools() {
        return tools != null ? tools : List.of();
    }

    /** Null on a real call; a simulation's canned tool answers otherwise. */
    public Map<String, String> toolMocks() {
        return toolMocks;
    }

    public void recordSimulatedToolCall(String line) {
        simulatedToolCalls.add(line);
    }

    /** The tool calls answered since the last drain, oldest first; the list is emptied. */
    public List<String> drainSimulatedToolCalls() {
        List<String> calls = List.copyOf(simulatedToolCalls);
        simulatedToolCalls.clear();
        return calls;
    }

    public AgentPersona agentPersona() {
        return agentPersona != null ? agentPersona : AgentPersona.AI_ASSISTANT;
    }

    public EffectiveAiModelConfig aiModel() {
        return aiModel;
    }

    public String fastModel() {
        return fastModel;
    }

    public void setFastModel(String fastModel) {
        this.fastModel = fastModel;
    }

    public ChatClient ownChatClient() {
        return ownChatClient;
    }

    public void setOwnChatClient(ChatClient ownChatClient) {
        this.ownChatClient = ownChatClient;
    }

    public EffectiveVoiceSettings voiceSettings() {
        return voiceSettings;
    }

    public boolean emotionAdaptiveVoice() {
        return emotionAdaptiveVoice;
    }

    public CustomerSentiment lastCustomerSentiment() {
        return lastCustomerSentiment;
    }

    public void setLastCustomerSentiment(CustomerSentiment sentiment) {
        this.lastCustomerSentiment = sentiment != null ? sentiment : CustomerSentiment.NEUTRAL;
    }

    @Override
    public void noteCallerMood(CustomerSentiment mood) {
        setLastCustomerSentiment(mood);
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

    public Runnable transfer() {
        return transfer;
    }

    public long callAttemptId() {
        return callAttemptId;
    }

    public long companyId() {
        return companyId;
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

    /**
     * Speak the rest of this call in {@code language}: the caller answered in the other
     * one of the campaign's languages ({@code LanguageDetector}).
     *
     * <p>Three things follow the language and are all switched here. The voice becomes
     * the campaign's voice for it — a voice only speaks the language it was recorded in,
     * and one mapped to no voice falls back to the provider's own default for that
     * language. The system prompt is dropped so the next turn rebuilds it, since it is
     * what tells the model which language to answer in; the cache hit on the old prefix
     * is worth less than answering the caller in their language. The history is left
     * alone on purpose — what was already said stays said, and the model needs it.
     */
    public void switchLanguage(String language) {
        this.language = language;
        String voice = languageVoices.get(language);
        this.ttsVoice = (voice != null && !voice.isBlank()) ? voice : null;
        this.systemPrefix = null;
        resetLanguageCandidate();
    }

    /**
     * Note that the caller answered in {@code language} while this call is being held in
     * another one, and return how many finals in a row have now said so.
     */
    public int noteLanguageCandidate(String language) {
        if (language.equals(candidateLanguage)) {
            return ++candidateLanguageTurns;
        }
        candidateLanguage = language;
        candidateLanguageTurns = 1;
        return 1;
    }

    /** The caller is speaking the language this call is already in. */
    public void resetLanguageCandidate() {
        candidateLanguage = null;
        candidateLanguageTurns = 0;
    }

    public String companyName() {
        return companyName;
    }

    public String companyDisclosureText() {
        return companyDisclosureText;
    }

    public boolean disclosureEnabled() {
        return disclosureEnabled;
    }

    public CallContext context() {
        return context;
    }

    public ScenarioDefinition scenario() {
        return scenario;
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
     * Hand the rest of this call to {@code next} ({@link MultiAgentRouter}): its first stage
     * becomes the current one, and the cached prompt prefix is dropped because that is
     * where the scenario's role and rules live. The history stays — what was already said
     * stays said, and the new scenario's model needs it.
     */
    public void switchScenario(ScenarioDefinition next) {
        this.scenario = next;
        setState(next.stages().get(0).id());
        this.systemPrefix = null;
    }

    public String operatorWhisper() {
        return operatorWhisper;
    }

    public void setOperatorWhisper(String operatorWhisper) {
        this.operatorWhisper = operatorWhisper;
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

    // ------------------------------------------------------------------
    // FSM, lifecycle and outcome
    // ------------------------------------------------------------------

    public String state() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
        if (state != null) {
            stageAttempts.putIfAbsent(state, 0);
        }
    }

    public int recordStageAttempt(String stage) {
        String key = stage != null ? stage : this.state;
        return stageAttempts.merge(key, 1, Integer::sum);
    }

    public int stageAttempts(String stage) {
        String key = stage != null ? stage : this.state;
        return stageAttempts.getOrDefault(key, 0);
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean isLineGone() {
        return lineGone;
    }

    /** Called once the call is over on the line itself — {@code DialogEngine.endCall}. */
    public void markLineGone() {
        this.lineGone = true;
    }

    public void end(Disposition disposition) {
        this.ended = true;
        if (disposition != null) {
            outcomeLedger.setDisposition(disposition);
        }
    }

    /** Claim the hang-up; true for exactly one caller. */
    public boolean beginClosing() {
        return closing.compareAndSet(false, true);
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

    public Disposition disposition() {
        return outcomeLedger.disposition();
    }

    @Override
    public void setDisposition(Disposition disposition) {
        outcomeLedger.setDisposition(disposition);
        if (disposition != null && outcomeTurn < 0) {
            outcomeTurn = turnState.turnCount();
        }
    }

    /**
     * Turns the call has taken since an outcome was first recorded, or {@code -1} while
     * none has been. The outcome tools that do not end the call themselves
     * (recordPaymentPromise, recordRefusalReason, scheduleCallback) leave a conversation
     * with nothing left to agree, and the model does not always notice — this is what
     * bounds how long it may go on not noticing.
     */
    public int turnsSinceOutcome() {
        return outcomeTurn < 0 ? -1 : turnState.turnCount() - outcomeTurn;
    }

    /** Record one outcome field a tool call produced (e.g. {@code "promisedDate"}). */
    @Override
    public void recordOutcome(String key, Object value) {
        outcomeLedger.recordOutcome(key, value);
    }

    /** Everything tools have recorded so far this call — for the live operator/monitoring view. */
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

    @Override
    public PendingToolConfirmation findPendingConfirmation(String toolName) {
        return outcomeLedger.findPendingConfirmation(toolName);
    }

    @Override
    public void setPendingConfirmation(String toolName, PendingToolConfirmation pending) {
        outcomeLedger.setPendingConfirmation(toolName, pending);
    }

    /**
     * Counted from the history rather than the turn counter, because the evaluation's
     * simulated calls run turns without one. Only pathologically long calls trim the
     * history, and then a held write is simply read back once more.
     */
    @Override
    public int countCallerUtterances() {
        int count = 0;
        for (Message message : history) {
            if (isCallerLine(message)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String findLastCallerText() {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            if (isCallerLine(message)) {
                return message.getText();
            }
        }
        return null;
    }

    /** A caller's line — not one of the notes this system adds to the history in the user's role. */
    private static boolean isCallerLine(Message message) {
        return message instanceof UserMessage && !SystemPromptFactory.isSystemNote(message.getText());
    }

    public String doNotCallReason() {
        return outcomeLedger.doNotCallReason();
    }

    @Override
    public void setDoNotCallReason(String doNotCallReason) {
        outcomeLedger.setDoNotCallReason(doNotCallReason);
    }

    public String transferReason() {
        return outcomeLedger.transferReason();
    }

    @Override
    public void setTransferReason(String reason) {
        outcomeLedger.setTransferReason(reason);
    }

    /**
     * Wires this call's keypad. Set once by the engine when the call has a real Asterisk
     * channel behind it; left null for simulated runs, where {@link #sendDtmf} then tells
     * the model the truth rather than silently doing nothing.
     */
    public void setDtmfSender(Consumer<String> sender) {
        this.dtmfSender = sender;
    }

    public List<String> detectLanguages() {
        return detectLanguages;
    }

    /** Switches only to a language this company's calls are held in ({@link #detectLanguages}). */
    @Override
    public boolean switchCallLanguage(String requested) {
        if (requested == null) {
            return false;
        }
        for (String supported : detectLanguages) {
            if (supported.equalsIgnoreCase(requested.strip())) {
                if (!supported.equalsIgnoreCase(language)) {
                    switchLanguage(supported);
                }
                return true;
            }
        }
        return false;
    }

    public void setDetectLanguages(List<String> detectLanguages) {
        this.detectLanguages = detectLanguages == null ? List.of() : List.copyOf(detectLanguages);
    }

    @Override
    public int countToolCall(String toolName) {
        return outcomeLedger.countToolCall(toolName);
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

    // ------------------------------------------------------------------
    // Turn machinery — TurnState
    // ------------------------------------------------------------------

    public AtomicBoolean busy() {
        return turnState.busy();
    }

    public boolean isInterrupted() {
        return turnState.isInterrupted();
    }

    public void setInterrupted(boolean interrupted) {
        turnState.setInterrupted(interrupted);
    }

    @Override
    public void waitForCaller() {
        waitingForCaller = true;
    }

    /** Whether this turn's model chose to keep listening instead of answering. */
    public boolean isWaitingForCaller() {
        return waitingForCaller;
    }

    public void clearWaitForCaller() {
        waitingForCaller = false;
    }

    /**
     * The bot was paused on caller speech and is waiting for words to say whether that
     * was an interruption. Returns the token {@link #settleBargeIn(long)} takes.
     */
    public long markBargeInPending() {
        long token = System.nanoTime();
        bargeInPendingSince.set(token);
        return token;
    }

    public boolean isBargeInPending() {
        return bargeInPendingSince.get() != 0;
    }

    /** Settle the pending barge-in, once: true for the one caller that got to decide it. */
    public boolean settleBargeIn() {
        return bargeInPendingSince.getAndSet(0) != 0;
    }

    /** As {@link #settleBargeIn()}, only if it is still the barge-in {@code token} was issued for. */
    public boolean settleBargeIn(long token) {
        return bargeInPendingSince.compareAndSet(token, 0);
    }

    public int turnCount() {
        return turnState.turnCount();
    }

    public int incrementTurn() {
        return turnState.incrementTurn();
    }

    /** Start the turnaround clock — the client just stopped speaking. */
    public void startTurnClock() {
        turnState.startTurnClock();
    }

    /** This turn's stage-by-stage stamps (§1.3 breakdown). */
    public TurnLatency latency() {
        return turnState.latency();
    }

    /** Whether the recognizer was unsure of the words the current turn is answering. */
    public boolean isLowConfidenceInput() {
        return turnState.isLowConfidenceInput();
    }

    public void setLowConfidenceInput(boolean lowConfidenceInput) {
        turnState.setLowConfidenceInput(lowConfidenceInput);
    }

    /** Knowledge-base passages the current turn may answer from; never null. */
    public List<KnowledgePassage> knowledgePassages() {
        return turnState.knowledgePassages();
    }

    public void setKnowledgePassages(List<KnowledgePassage> passages) {
        turnState.setKnowledgePassages(passages);
    }

    /** See {@link TurnState#claimBackchannel}. */
    public boolean claimBackchannel(int afterMs) {
        return turnState.claimBackchannel(afterMs);
    }

    /** See {@link TurnState#claimInterjection}. */
    public boolean claimInterjection(int afterMs) {
        return turnState.claimInterjection(afterMs);
    }

    /** The caller finished: the next answer starts its own clock and earns its own "aha". */
    public void clientAnswerEnded() {
        turnState.clientAnswerEnded();
    }

    /** See {@link TurnState#takeTurnaround}. */
    public Duration takeTurnaround() {
        return turnState.takeTurnaround();
    }

    /** See {@link TurnState#isFirstAudioPending}. */
    public boolean isFirstAudioPending() {
        return turnState.isFirstAudioPending();
    }

    /** Queue client speech that could not be handled now; merged with anything already waiting. */
    public void deferInput(String text) {
        turnState.deferInput(text);
    }

    /** Take the deferred client speech, or {@code null} if there is none. */
    public String takeDeferredInput() {
        return turnState.takeDeferredInput();
    }

    /** Remember a caller utterance the bot never got to answer; {@code null} forgets it. */
    public void setUnansweredClientText(String text) {
        turnState.setUnansweredClientText(text);
    }

    /** Take the unanswered utterance, or {@code null} if the last turn was answered. */
    public String takeUnansweredClientText() {
        return turnState.takeUnansweredClientText();
    }

    /** Whether the recognizer has heard the caller within the last {@code withinMs}. */
    public boolean heardCallerWithin(long withinMs) {
        return turnState.heardCallerWithin(withinMs);
    }

    /** See {@link TurnState#setSpeculation}. */
    public void setSpeculation(Speculation next) {
        turnState.setSpeculation(next);
    }

    /** Non-consuming peek at the parked speculative reply, or {@code null} if there is none. */
    public Speculation speculation() {
        return turnState.speculation();
    }

    /** Take the parked speculative reply, leaving none behind. */
    public Speculation takeSpeculation() {
        return turnState.takeSpeculation();
    }

    /** The recognizer delivered another interim hypothesis for the utterance in progress. */
    public void noteInterim() {
        turnState.noteInterim();
    }

    /** See {@link TurnState#heardCallerWithin}: true while a typed number is still coming in. */
    public void setTyping(boolean typing) {
        turnState.setTyping(typing);
    }

    public int interimsThisTurn() {
        return turnState.interimsThisTurn();
    }

    /** Why the last interim was not worth guessing on, or {@code null} if none was turned away. */
    public String speculationBlocker() {
        return turnState.speculationBlocker();
    }

    public void setSpeculationBlocker(String speculationBlocker) {
        turnState.setSpeculationBlocker(speculationBlocker);
    }

    /** Start the next turn's count; called once the turn has reported what this one did. */
    public void resetSpeculationDiagnostics() {
        turnState.resetSpeculationDiagnostics();
    }

    /** Cancel and drop any parked speculative reply. */
    public void discardSpeculation() {
        turnState.discardSpeculation();
    }

    /** Record the line a tool call carried; a blank one is no line at all. */
    public void addToolReply(String reply) {
        turnState.addToolReply(reply);
    }

    /** See {@link TurnState#toolReplies}. */
    public String toolReplies() {
        return turnState.toolReplies();
    }

    /** Drop the previous turn's lines before a new turn calls its tools. */
    public void clearToolReplies() {
        turnState.clearToolReplies();
    }

    public boolean isCancelled() {
        return turnState.isCancelled();
    }

    public void setCancelled(boolean cancelled) {
        turnState.setCancelled(cancelled);
    }

    /** See {@link TurnState#clearCancelledBefore}. */
    public void clearCancelledBefore(long startNanos) {
        turnState.clearCancelledBefore(startNanos);
    }

    /** @return how many sentences in a row have now failed to synthesize */
    public int recordTtsFailure() {
        return turnState.recordTtsFailure();
    }

    public void clearTtsFailures() {
        turnState.clearTtsFailures();
    }

    // ------------------------------------------------------------------
    // What the caller heard — SpokenAudioLedger
    // ------------------------------------------------------------------

    /** Drop the previous turn's spoken text before a new turn starts producing its own. */
    public void clearSpokenText() {
        spokenAudioLedger.clearSpokenText();
    }

    /** Note that {@code text} was queued for the caller and is therefore heard. */
    public void appendSpokenText(String text) {
        spokenAudioLedger.appendSpokenText(text);
    }

    /** See {@link SpokenAudioLedger#appendSpokenAudio}. */
    public void appendSpokenAudio(String text, long startSample, int samples) {
        spokenAudioLedger.appendSpokenAudio(text, startSample, samples);
    }

    /** What the caller has heard of this turn, or {@code null} if nothing — see {@link SpokenAudioLedger#spokenText}. */
    public String spokenText() {
        return spokenAudioLedger.spokenText();
    }

    /** Hold the tail of a reply a barge-in cut off, in case the interruption was noise. */
    public void setUnspokenText(String text) {
        spokenAudioLedger.setUnspokenText(text);
    }

    /** Take the interrupted reply's unspoken tail, or {@code null} if there is none. */
    public String takeUnspokenText() {
        return spokenAudioLedger.takeUnspokenText();
    }

    /** See {@link SpokenAudioLedger#hasUnspokenText}. */
    public boolean hasUnspokenText() {
        return spokenAudioLedger.hasUnspokenText();
    }

    // ------------------------------------------------------------------
    // Spend and speed — UsageLedger
    // ------------------------------------------------------------------

    /** Add a turn's token usage to the call total and return the new total. */
    public long addTokens(long tokens) {
        return usageLedger.addTokens(tokens);
    }

    public long tokensUsed() {
        return usageLedger.tokensUsed();
    }

    /** Add one turn's token breakdown to the call's running totals (§10.5 "Texnik" tab). */
    public void addTokenBreakdown(long prompt, long completion, long cached) {
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

    /** Count one line's characters towards what this call is billed for. */
    public void addTtsChars(String text) {
        usageLedger.addTtsChars(text);
    }

    public long ttsChars() {
        return usageLedger.ttsChars();
    }

    /** Record one turn's client-stopped-talking -> first-audio-queued latency. */
    public void recordTurnLatency(long ms) {
        usageLedger.recordTurnLatency(ms);
    }

    public Integer avgTurnLatencyMs() {
        return usageLedger.avgTurnLatencyMs();
    }

    public Integer maxTurnLatencyMs() {
        return usageLedger.maxTurnLatencyMs();
    }

    /** One turn's stage timings, kept for the call's timeline (written at teardown). */
    public void recordTurnTiming(TurnTiming timing) {
        usageLedger.recordTurnTiming(timing);
    }

    public List<TurnTiming> turnTimings() {
        return usageLedger.turnTimings();
    }

    /** Record one turn's LLM-only wall time. */
    public void recordLlmLatency(long ms) {
        usageLedger.recordLlmLatency(ms);
    }

    public Integer avgLlmLatencyMs() {
        return usageLedger.avgLlmLatencyMs();
    }

    public Integer maxLlmLatencyMs() {
        return usageLedger.maxLlmLatencyMs();
    }

    public int recordFactViolation() {
        return usageLedger.recordFactViolation();
    }

    public int factViolations() {
        return usageLedger.factViolations();
    }

    public void recordBargeIn() {
        usageLedger.recordBargeIn();
    }

    public int bargeIns() {
        return usageLedger.bargeIns();
    }

    public void recordLowConfidenceInput() {
        usageLedger.recordLowConfidenceInput();
    }

    public int lowConfidenceInputs() {
        return usageLedger.lowConfidenceInputs();
    }
}
