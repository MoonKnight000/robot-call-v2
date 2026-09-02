package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.ai.chat.messages.Message;

import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
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
    private final long callAttemptId;
    /** This call's company's AI-model overrides merged over the process defaults (§11 settings), resolved once. */
    private final EffectiveAiModelConfig aiModel;
    /** This call's company's TTS overrides (§11 settings/voice), resolved once. */
    private final EffectiveVoiceSettings voiceSettings;
    private final boolean emotionAdaptiveVoice;
    private volatile uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment lastCustomerSentiment = uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment.NEUTRAL;
    /** Campaign's own choice on the §11.1 opening disclosure (§10.6); the engine also
     * checks the global {@code mandatory-disclosure} kill-switch on top of this. */
    private final boolean disclosureEnabled;

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
     * Turn number the last "bir soniya" filler was played on, or 0 for none. A filler
     * every turn is worse than none at all — it stops reading as thinking and starts
     * reading as a tic — so a turn only gets one if the previous turn did not.
     */
    private volatile int lastFillerTurn;

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
     * The tail of a reply a barge-in stopped before it could be spoken, kept in case the
     * interruption turns out to have been noise. Cleared as soon as it is resumed or a
     * real turn makes it stale.
     */
    private final AtomicReference<String> unspokenText = new AtomicReference<>();

    /**
     * A reply started while the caller was still speaking, waiting to find out whether
     * they said what the recognizer guessed they were saying ({@link Speculation}).
     */
    private final AtomicReference<Speculation> speculation = new AtomicReference<>();

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
                         long callAttemptId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings, boolean emotionAdaptiveVoice,
                         Map<String, String> languageVoices) {
        this.channelId = channelId;
        this.language = language;
        this.ttsVoice = ttsVoice;
        this.languageVoices = languageVoices == null ? Map.of() : Map.copyOf(languageVoices);
        this.context = context;
        this.scenario = scenario;
        // The scenario's first declared stage is its entry point, by convention
        // (ROADMAP A.3 — every builtin template already lists its opening stage first).
        this.state = scenario.stages().get(0).id();
        this.endpoint = endpoint;
        this.hangup = hangup;
        this.transfer = transfer;
        this.callAttemptId = callAttemptId;
        this.watchdog = watchdog;
        this.disclosureEnabled = disclosureEnabled;
        this.companyName = companyName;
        this.companyDisclosureText = companyDisclosureText;
        this.aiModel = aiModel;
        this.voiceSettings = voiceSettings;
        this.emotionAdaptiveVoice = emotionAdaptiveVoice;
    }

    public DialogSession(String channelId, String language, String ttsVoice, CallContext context,
                         ScenarioDefinition scenario, RtpEndpoint endpoint, Runnable hangup, Runnable transfer,
                         long callAttemptId, NoInputWatchdog watchdog, boolean disclosureEnabled,
                         String companyName, String companyDisclosureText, EffectiveAiModelConfig aiModel,
                         EffectiveVoiceSettings voiceSettings) {
        this(channelId, language, ttsVoice, context, scenario, endpoint, hangup, transfer, callAttemptId,
                watchdog, disclosureEnabled, companyName, companyDisclosureText, aiModel, voiceSettings, true,
                Map.of());
    }

    public EffectiveAiModelConfig aiModel() {
        return aiModel;
    }

    public EffectiveVoiceSettings voiceSettings() {
        return voiceSettings;
    }

    public boolean emotionAdaptiveVoice() {
        return emotionAdaptiveVoice;
    }

    public uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment lastCustomerSentiment() {
        return lastCustomerSentiment;
    }

    public void setLastCustomerSentiment(uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment sentiment) {
        this.lastCustomerSentiment = sentiment != null ? sentiment : uz.murodjon.robotcallv2.agent.dialog.SentimentDetector.CustomerSentiment.NEUTRAL;
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

    public String state() {
        return state;
    }

    public void setState(String state) {
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

    /**
     * Whether a filler may be played on {@code turn} — i.e. the previous turn did not
     * already get one.
     */
    public boolean fillerAllowed(int turn) {
        return turn - lastFillerTurn >= 2;
    }

    /** Note that {@code turn} used its filler, so the next turn does not. */
    public void markFillerSpoken(int turn) {
        lastFillerTurn = turn;
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

    /** What the caller has heard of this turn so far, or {@code null} if nothing. */
    public synchronized String spokenText() {
        return spokenText.isEmpty() ? null : spokenText.toString();
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
}
