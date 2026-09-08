package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.agent.metrics.TurnLatency;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Mutable in-memory state for one call's conversation (PROJECT.md §4, §5.1). Holds
 * the FSM state, the chat history sent to the LLM each turn, guardrail counters, and
 * the outputs a turn's tool calls record. Not thread-safe by itself — {@code busy}
 * serializes turns and the engine mutates it on a single worker thread at a time.
 */
public class DialogSession implements DialogOutcomeSink {

    private final String channelId;
    /**
     * The language this call is being spoken in. Starts as the one the target was dialled
     * with and changes if the caller turns out to speak the other one
     * ({@link #switchLanguage}) — the campaign is bilingual, the caller is not.
     */
    private volatile String language;
    /** Catalog id of the voice speaking this call (§2.5); null = configured routing. */
    private volatile String ttsVoice;
    /** The campaign's voice per language, used when the call switches language. */
    private final Map<String, String> languageVoices;
    /** Silence watchdog for this call; null when the watchdog is disabled. */
    private final NoInputWatchdog watchdog;
    private final CallContext context;
    /** The scenario this call runs (ROADMAP A.3) — resolved once at {@code startCall} and reused for the whole call. */
    private final ScenarioDefinition scenario;
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
    private final List<PronunciationRule> pronunciationRules;
    private final AiAgent agent;
    private volatile Consumer<String> midCallSmsSender;

    /** Whose name the §11.1 disclosure is spoken in — the company this call's campaign belongs to. */
    private final String companyName;
    /** That company's own wording of the §11.1 disclosure (company config), resolved once. */
    private final String companyDisclosureText;
    private final List<Message> history = new ArrayList<>();
    private final Instant startedAt = Instant.now();
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /** Current stage id — a scenario StageDef.id(), not an enum (ROADMAP A.3). */
    private volatile String state;
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
    private int turnCount;

    /**
     * Set when the client's turn ends, cleared once the bot's first audio for that
     * turn is queued — the two ends of the &lt;1s turnaround budget (§1.3). Null for
     * the opening greeting, where nobody was waiting.
     */
    private volatile Instant turnStartedAt;
    private final AtomicBoolean turnaroundRecorded = new AtomicBoolean(true);

    /** Where this turn's wait went, stage by stage — the breakdown behind that budget. */
    private final TurnLatency latency = new TurnLatency();

    /** When the answer the caller is currently giving began, or null between answers. */
    private Instant clientSpeechStartedAt;
    /** Whether that answer has already had its one "aha" (§ backchannel-after-ms). */
    private boolean backchannelSpoken;
    /** Whether the bot has already stepped into that answer (§ interject-after-ms). */
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

    /**
     * What this turn actually put on the wire, as opposed to what the model wrote.
     *
     * <p>The two are the same turn after turn until a barge-in, and then they are not: the
     * caller heard the first sentence and the remaining three were never synthesized. The
     * history, the transcript and the "you were cut off here" note all have to describe
     * what the caller <em>heard</em> — told that it said all four, the model treats the
     * unheard three as delivered and never comes back to them.
     */
    private final StringBuilder spokenText = new StringBuilder();

    /**
     * This turn's lines that went out as audio, with where each one sits in the playback
     * stream. What the caller heard of them depends on how far the wire got before a
     * barge-in flushed the queue, so it is worked out when it is asked for, not when the
     * audio was handed over.
     */
    private final List<SpokenAudio> spokenAudio = new ArrayList<>();

    /**
     * The tail of a reply a barge-in stopped before it could be spoken, kept in case the
     * interruption turns out to have been noise. Cleared as soon as it is resumed or a
     * real turn makes it stale.
     */
    private final AtomicReference<String> unspokenText = new AtomicReference<>();

    /**
     * A caller utterance whose turn was cancelled before one word of the reply went out:
     * the gate closed, the caller was in fact still talking, and their next final is the
     * rest of the same sentence. {@code TurnRunner} folds the two into one turn.
     */
    private final AtomicReference<String> unansweredClientText = new AtomicReference<>();

    /** When the recognizer last produced an interim — the caller was speaking then. */
    private volatile long lastInterimAtMs;

    /**
     * A reply started while the caller was still speaking, waiting to find out whether
     * they said what the recognizer guessed they were saying ({@link Speculation}).
     */
    private final AtomicReference<Speculation> speculation = new AtomicReference<>();

    /**
     * What happened to this turn's guessing, for the one summary line the turn logs
     * ({@code TurnRunner#adoptSpeculation}). An utterance produces a dozen interims and
     * logging each of them buries the call; a count and the last reason one was turned
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

    // Recorded by tool calls. disposition is read at teardown (CampaignService.applyOutcome,
    // CallFinalizer). outcome is a live-monitoring convenience only (operatorSnapshot) — the
    // persisted call_result.outcome comes from SummaryService's independent post-call pass
    // over the transcript, not from what fired live (ROADMAP A.3).
    private Disposition disposition;
    /** {@link #turnCount} when the first outcome was recorded; -1 until one is. */
    private volatile int outcomeTurn = -1;
    private final Map<String, Object> outcome = new ConcurrentHashMap<>();
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

    // Token/latency breakdown for the "Texnik" tab (§10.5) — Micrometer's
    // voice_llm_tokens_*/voice_llm_turn_latency metrics are global counters with no
    // per-call correlation (see ROADMAP), so this call's numbers have to be accumulated
    // here and read once at teardown, before the session is dropped.
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

    /** Cancels the silence watchdog when the call ends; null when it is disabled. */
    private volatile ScheduledFuture<?> watchdogTask;

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         Map<String, String> languageVoices) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer,
                callAttemptId, companyId, watchdog, disclosureEnabled, companyName, companyDisclosureText,
                aiModel, voiceSettings, emotionAdaptiveVoice, AgentPersona.AI_ASSISTANT, languageVoices);
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         AgentPersona agentPersona,
                         Map<String, String> languageVoices) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer,
                callAttemptId, companyId, watchdog, disclosureEnabled, companyName, companyDisclosureText,
                aiModel, voiceSettings, emotionAdaptiveVoice, agentPersona, languageVoices, null, List.of());
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         AgentPersona agentPersona,
                         Map<String, String> languageVoices,
                         String firstMessage) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer,
                callAttemptId, companyId, watchdog, disclosureEnabled, companyName, companyDisclosureText,
                aiModel, voiceSettings, emotionAdaptiveVoice, agentPersona, languageVoices, firstMessage, List.of(), List.of());
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         AgentPersona agentPersona,
                         Map<String, String> languageVoices,
                         String firstMessage,
                         List<Tool> tools) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer,
                callAttemptId, companyId, watchdog, disclosureEnabled, companyName, companyDisclosureText,
                aiModel, voiceSettings, emotionAdaptiveVoice, agentPersona, languageVoices, firstMessage, tools, List.of());
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         AgentPersona agentPersona,
                         Map<String, String> languageVoices,
                         String firstMessage,
                         List<Tool> tools,
                         List<PronunciationRule> pronunciationRules) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer,
                callAttemptId, companyId, watchdog, disclosureEnabled, companyName, companyDisclosureText,
                aiModel, voiceSettings, emotionAdaptiveVoice, agentPersona, languageVoices,
                firstMessage, tools, pronunciationRules, null);
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         AgentPersona agentPersona,
                         Map<String, String> languageVoices,
                         String firstMessage,
                         List<Tool> tools,
                         List<PronunciationRule> pronunciationRules,
                         AiAgent agent) {
        this.channelId = channelId;
        this.language = language;
        this.ttsVoice = ttsVoice;
        this.languageVoices = languageVoices == null ? Map.of() : Map.copyOf(languageVoices);
        this.context = context;
        this.scenario = scenario;
        // The scenario's first declared stage is its entry point, by convention
        // (ROADMAP A.3 — every builtin template already lists its opening stage first).
        this.state = (scenario != null && scenario.stages() != null && !scenario.stages().isEmpty())
                ? scenario.stages().get(0).id()
                : "CONVERSATION";
        this.endpoint = endpoint;
        this.hangup = hangup;
        this.transfer = transfer;
        this.callAttemptId = callAttemptId;
        this.companyId = companyId;
        this.watchdog = watchdog;
        this.disclosureEnabled = disclosureEnabled;
        this.agentPersona = agentPersona != null ? agentPersona : AgentPersona.AI_ASSISTANT;
        this.companyName = companyName;
        this.companyDisclosureText = companyDisclosureText;
        this.aiModel = aiModel;
        this.voiceSettings = voiceSettings;
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
        this.firstMessage = firstMessage;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.pronunciationRules = pronunciationRules == null ? List.of() : List.copyOf(pronunciationRules);
        this.agent = agent;
    }

    public List<PronunciationRule> pronunciationRules() {
        return pronunciationRules != null ? pronunciationRules : List.of();
    }

    public AiAgent agent() {
        return agent;
    }

    public void setMidCallSmsSender(Consumer<String> midCallSmsSender) {
        this.midCallSmsSender = midCallSmsSender;
    }

    public void triggerMidCallSms(String text) {
        if (midCallSmsSender != null && text != null && !text.isBlank()) {
            midCallSmsSender.accept(text.trim());
        }
    }

    public String firstMessage() {
        return firstMessage;
    }

    /**
     * The MCP tool callbacks for this call, resolved once.
     *
     * <p>Cached on the session because building them reads the company's connections, and
     * the tool list is re-sent on every turn — a query per turn would put a database round
     * trip inside the pause the caller hears.
     */
    private volatile List<ToolCallback> mcpTools;

    public List<ToolCallback> mcpTools() {
        return mcpTools;
    }

    public void setMcpTools(List<ToolCallback> mcpTools) {
        this.mcpTools = mcpTools;
    }

    public List<Tool> tools() {
        return tools != null ? tools : List.of();
    }

    public AgentPersona agentPersona() {
        return agentPersona != null ? agentPersona : AgentPersona.AI_ASSISTANT;
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, long companyId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer, callAttemptId,
                companyId, watchdog, disclosureEnabled, companyName, companyDisclosureText, aiModel, voiceSettings,
                true, Map.of());
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

    /** Add one turn's token breakdown to the call's running totals (§10.5 "Texnik" tab). */
    public void addTokenBreakdown(long prompt, long completion, long cached) {
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

    public long promptTokens() {
        return promptTokens.get();
    }

    public long completionTokens() {
        return completionTokens.get();
    }

    public long cachedTokens() {
        return cachedTokens.get();
    }

    /** Count one line's characters towards what this call is billed for. */
    public void addTtsChars(String text) {
        if (text != null && !text.isBlank()) {
            ttsChars.addAndGet(text.length());
        }
    }

    public long ttsChars() {
        return ttsChars.get();
    }

    /** Record one turn's client-stopped-talking -> first-audio-queued latency. */
    public void recordTurnLatency(long ms) {
        turnLatencySumMs.addAndGet(ms);
        turnLatencyCount.incrementAndGet();
        turnLatencyMaxMs.accumulateAndGet(ms, Math::max);
    }

    public Integer avgTurnLatencyMs() {
        int count = turnLatencyCount.get();
        return count == 0 ? null : (int) (turnLatencySumMs.get() / count);
    }

    public Integer maxTurnLatencyMs() {
        return turnLatencyCount.get() == 0 ? null : (int) turnLatencyMaxMs.get();
    }

    /** Record one turn's LLM-only wall time. */
    public void recordLlmLatency(long ms) {
        llmLatencySumMs.addAndGet(ms);
        llmLatencyCount.incrementAndGet();
        llmLatencyMaxMs.accumulateAndGet(ms, Math::max);
    }

    public Integer avgLlmLatencyMs() {
        int count = llmLatencyCount.get();
        return count == 0 ? null : (int) (llmLatencySumMs.get() / count);
    }

    public Integer maxLlmLatencyMs() {
        return llmLatencyCount.get() == 0 ? null : (int) llmLatencyMaxMs.get();
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

    private final Map<String, Integer> stageAttempts = new ConcurrentHashMap<>();

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
        latency.turnStarted();
    }

    /** This turn's stage-by-stage stamps (§1.3 breakdown). */
    public TurnLatency latency() {
        return latency;
    }

    /** Whether the recognizer was unsure of the words the current turn is answering. */
    public boolean isLowConfidenceInput() {
        return lowConfidenceInput;
    }

    public void setLowConfidenceInput(boolean lowConfidenceInput) {
        this.lowConfidenceInput = lowConfidenceInput;
    }

    /** Knowledge-base passages the current turn may answer from; never null. */
    public List<KnowledgePassage> knowledgePassages() {
        return knowledgePassages;
    }

    public void setKnowledgePassages(List<KnowledgePassage> passages) {
        this.knowledgePassages = passages == null ? List.of() : List.copyOf(passages);
    }

    /**
     * Whether the caller has now been talking for {@code afterMs} without a backchannel,
     * and claims the right to one if so — at most one per answer, so a long explanation
     * gets an "aha" rather than a chorus.
     *
     * <p>Timed from the first interim of the answer, which is the only signal this side
     * has that somebody is still mid-sentence. Reset by the final that ends it.
     */
    public synchronized boolean claimBackchannel(int afterMs) {
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
    public synchronized boolean claimInterjection(int afterMs) {
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
    public synchronized void clientAnswerEnded() {
        clientSpeechStartedAt = null;
        backchannelSpoken = false;
        interjected = false;
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

    /**
     * Non-consuming peek at whether {@link #takeTurnaround()} would still return a value —
     * i.e. whether the turn's first audio has not been queued yet. Lets a caller decide
     * *before* synthesizing whether this is the sentence the §1.3 clock is waiting on
     * (streaming-worthy) without tripping the once-per-turn flag itself.
     */
    public boolean isFirstAudioPending() {
        return turnStartedAt != null && !turnaroundRecorded.get();
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

    /** Drop the previous turn's spoken text before a new turn starts producing its own. */
    public synchronized void clearSpokenText() {
        spokenText.setLength(0);
        spokenAudio.clear();
    }

    /** Note that {@code text} was queued for the caller and is therefore heard. */
    public synchronized void appendSpokenText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!spokenText.isEmpty()) {
            spokenText.append(' ');
        }
        spokenText.append(text.trim());
    }

    /**
     * Note that {@code text} was queued as audio occupying {@code samples} samples starting
     * at {@code startSample} of this call's playback stream.
     *
     * <p>Queued is not heard. A sentence sits in the endpoint's queue for as long as it
     * takes to say, and a barge-in three words in throws away the rest — so what the caller
     * heard of it is decided later, by how far the wire actually got ({@link #spokenText()}).
     * Recorded even when the turn is already cancelled: the words that did go out were
     * still heard, and the model has to know that they were.
     */
    public synchronized void appendSpokenAudio(String text, long startSample, int samples) {
        if (text == null || text.isBlank() || samples <= 0) {
            appendSpokenText(text);
            return;
        }
        spokenAudio.add(new SpokenAudio(text.trim(), startSample, samples));
    }

    /**
     * What the caller has heard of this turn, or {@code null} if nothing.
     *
     * <p>Lines queued without audio accounting count whole. Lines that were queued as audio
     * are measured against the endpoint's playback position: one fully played is whole, one
     * cut partway through is truncated at the last word the caller can have heard, and
     * anything behind that in the queue never reached them at all.
     */
    public synchronized String spokenText() {
        StringBuilder heard = new StringBuilder(spokenText);
        long played = endpoint != null ? endpoint.playedSamples() : Long.MAX_VALUE;
        for (SpokenAudio line : spokenAudio) {
            String part = line.heardPart(played);
            if (part == null) {
                break; // this line never started; nothing behind it did either
            }
            if (!heard.isEmpty()) {
                heard.append(' ');
            }
            heard.append(part);
            if (part.length() < line.text().length()) {
                break; // cut off inside this line
            }
        }
        return heard.isEmpty() ? null : heard.toString();
    }

    /** Hold the tail of a reply a barge-in cut off, in case the interruption was noise. */
    public void setUnspokenText(String text) {
        unspokenText.set(text == null || text.isBlank() ? null : text.trim());
    }

    /** Take the interrupted reply's unspoken tail, or {@code null} if there is none. */
    public String takeUnspokenText() {
        return unspokenText.getAndSet(null);
    }

    /**
     * Non-consuming peek at whether a cut-off reply is still waiting to be resumed — i.e.
     * whether there is anything to go back to if the interruption turns out not to have
     * been one.
     */
    public boolean hasUnspokenText() {
        return unspokenText.get() != null;
    }

    /** Remember a caller utterance the bot never got to answer; {@code null} forgets it. */
    public void setUnansweredClientText(String text) {
        unansweredClientText.set(text == null || text.isBlank() ? null : text.trim());
    }

    /** Take the unanswered utterance, or {@code null} if the last turn was answered. */
    public String takeUnansweredClientText() {
        return unansweredClientText.getAndSet(null);
    }

    /** Whether the recognizer has heard the caller within the last {@code withinMs}. */
    public boolean heardCallerWithin(long withinMs) {
        long at = lastInterimAtMs;
        return at != 0 && System.currentTimeMillis() - at <= withinMs;
    }

    /**
     * Park a speculative reply. Anything it supersedes is cancelled: the caller has said
     * more since, so the older hypothesis is answering half a sentence.
     */
    public void setSpeculation(Speculation next) {
        Speculation previous = speculation.getAndSet(next);
        if (previous != null) {
            previous.discard();
        }
    }

    /** Non-consuming peek at the parked speculative reply, or {@code null} if there is none. */
    public Speculation speculation() {
        return speculation.get();
    }

    /** Take the parked speculative reply, leaving none behind. */
    public Speculation takeSpeculation() {
        return speculation.getAndSet(null);
    }

    /** The recognizer delivered another interim hypothesis for the utterance in progress. */
    public void noteInterim() {
        interimsThisTurn.incrementAndGet();
        lastInterimAtMs = System.currentTimeMillis();
    }

    public int interimsThisTurn() {
        return interimsThisTurn.get();
    }

    /** Why the last interim was not worth guessing on, or {@code null} if none was turned away. */
    public String speculationBlocker() {
        return speculationBlocker;
    }

    public void setSpeculationBlocker(String speculationBlocker) {
        this.speculationBlocker = speculationBlocker;
    }

    /** Start the next turn's count; called once the turn has reported what this one did. */
    public void resetSpeculationDiagnostics() {
        interimsThisTurn.set(0);
        speculationBlocker = null;
    }

    /** Cancel and drop any parked speculative reply. */
    public void discardSpeculation() {
        Speculation parked = speculation.getAndSet(null);
        if (parked != null) {
            parked.discard();
        }
    }

    /** Record the line a tool call carried; a blank one is no line at all. */
    public void addToolReply(String reply) {
        if (reply != null && !reply.isBlank()) {
            toolReplies.add(reply.trim());
        }
    }

    /**
     * This turn's tool-carried lines as one utterance, or {@code null} if the tools
     * brought none. Several tools in one turn read as consecutive sentences — "va'dangizni
     * belgilab qo'ydim" then "xayr" — which is how the model wrote them.
     */
    public String toolReplies() {
        return toolReplies.isEmpty() ? null : String.join(" ", toolReplies);
    }

    /** Drop the previous turn's lines before a new turn calls its tools. */
    public void clearToolReplies() {
        toolReplies.clear();
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
        if (disposition != null && outcomeTurn < 0) {
            outcomeTurn = turnCount;
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
        return outcomeTurn < 0 ? -1 : turnCount - outcomeTurn;
    }

    /** Record one outcome field a tool call produced (e.g. {@code "promisedDate"}). */
    public void recordOutcome(String key, Object value) {
        if (value != null) {
            outcome.put(key, value);
        }
    }

    /** Everything tools have recorded so far this call — for the live operator/monitoring view. */
    public Map<String, Object> outcome() {
        return Map.copyOf(outcome);
    }

    public String doNotCallReason() {
        return doNotCallReason;
    }

    public void setDoNotCallReason(String doNotCallReason) {
        this.doNotCallReason = doNotCallReason;
    }

    /**
     * Wires this call's keypad. Set once by the engine when the call has a real Asterisk
     * channel behind it; left null for simulated runs, where {@link #sendDtmf} then tells
     * the model the truth rather than silently doing nothing.
     */
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

    /**
     * One line of this turn as it sits in the playback stream: the words, and the samples
     * they occupy.
     *
     * @param startSample where the line begins in the endpoint's playback stream
     * @param samples     how long it is — i.e. where it ends
     */
    private record SpokenAudio(String text, long startSample, int samples) {

        /**
         * The part of this line the caller can have heard by the time the endpoint had
         * sent {@code played} samples, or {@code null} if none of it was.
         */
        String heardPart(long played) {
            if (played <= startSample) {
                return null;
            }
            if (played - startSample >= samples) {
                return text;
            }
            // Synthesis paces a sentence roughly evenly, so the share of its audio that
            // went out is the share of its words that were heard — give or take a word,
            // which is why the cut lands on a word boundary and never mid-word.
            int chars = (int) ((played - startSample) * text.length() / samples);
            int cut = text.lastIndexOf(' ', Math.min(chars, text.length() - 1));
            return cut <= 0 ? null : text.substring(0, cut);
        }
    }
}
