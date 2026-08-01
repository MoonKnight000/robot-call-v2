package uz.murodjon.uysotvoice.agent.dialog;

import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.agent.tts.TtsProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsRouter;
import uz.murodjon.uysotvoice.callrecord.service.CallRecordService;
import uz.murodjon.uysotvoice.live.enums.LiveEventType;
import uz.murodjon.uysotvoice.live.dto.LiveTranscriptEvent;
import uz.murodjon.uysotvoice.live.service.LiveBroadcastService;
import uz.murodjon.uysotvoice.shared.dialog.DialogPhrases;
import uz.murodjon.uysotvoice.shared.dialog.DialogState;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The FSM + LLM dialog engine (PROJECT.md §4, §7.1, Stage 7). Owns one LLM
 * ChatClient (Spring AI Google GenAI starter → the Gemini Developer API) and a per-call
 * {@link DialogSession} registry. Each
 * client final transcript triggers a turn: take the call's stable system prefix, call
 * the LLM with the running history, the state annex and the tools this state may use
 * (which may advance the FSM or record outcomes), then synthesize the reply and stream
 * it to the caller.
 *
 * <p>The request is assembled to stay cacheable: system prefix, then the history, then
 * everything that changes per turn. Providers price a repeated prefix at a fraction of
 * a fresh one, and a 25-turn call resends its whole context every turn — see
 * {@link SystemPromptFactory}. {@code voice.llm.tokens.cached} is where that shows up.
 *
 * <p>Turns run on a virtual-thread worker so the blocking LLM/TTS calls never stall
 * the STT/RTP threads. A per-session {@code busy} flag serializes turns and drops
 * finals that arrive while a turn is still in flight. Barge-in, sentence-level TTS
 * streaming, and DB/CRM persistence come in later stages (§7.2, Stage 9).
 *
 * <p>Non-fatal without credentials: if no LLM ChatModel is available (no
 * {@code GEMINI_API_KEY}), the app still runs and dialog is disabled.
 */
@Service
public class DialogEngine {

    private static final Logger log = LoggerFactory.getLogger(DialogEngine.class);

    /** Bootstraps the opening turn — the bot speaks first (there is no client input yet). */
    private static final String GREETING_BOOTSTRAP =
            "[TIZIM: Qo'ng'iroq ulandi, mijoz go'shakni ko'tardi. Rejaga muvofiq salomlashing.]";

    /**
     * Shortest fragment worth cutting into its own TTS request. Below this a "sentence"
     * sounds clipped and costs a round trip of its own.
     */
    private static final int MIN_SENTENCE_CHARS = 20;

    /** Cap on waiting for queued audio to drain before hanging up anyway. */
    private static final Duration MAX_DRAIN = Duration.ofSeconds(30);

    /**
     * How many messages to drop at once when the history cap is reached. Trimming in
     * a block rather than one per turn keeps the request prefix stable for longer,
     * which is what the provider's context cache is keyed on.
     */
    private static final int HISTORY_TRIM_BLOCK = 6;

    /**
     * Tools every state needs: the FSM has to be able to move, escalate, honour an
     * opt-out and hang up from anywhere in the call.
     */
    private static final Set<String> ALWAYS_AVAILABLE_TOOLS =
            Set.of("transitionTo", "requestHumanTransfer", "recordDoNotCall", "endCall");

    /**
     * How often the silence watchdog looks at a call. Finer than the idle threshold it
     * enforces, so the prompt lands close to the moment the threshold is crossed rather
     * than up to a whole threshold late.
     */
    private static final Duration WATCHDOG_TICK = Duration.ofSeconds(1);

    private final DialogProperties props;
    private final SystemPromptFactory promptFactory;
    private final TtsProperties ttsProps;
    private final TtsRouter ttsRouter;
    private final CallRecordService records;
    private final VoiceMetrics metrics;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final LiveBroadcastService broadcast;

    private final Map<String, DialogSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    /**
     * Drives the per-call silence watchdogs. One platform thread is enough: a tick only
     * reads two flags and a timestamp, and anything it decides to do is handed to the
     * virtual-thread worker — the scheduler must never block on TTS or a hangup.
     */
    private final ScheduledExecutorService watchdogScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dialog-watchdog");
                t.setDaemon(true);
                return t;
            });

    private volatile ChatClient chatClient;

    public DialogEngine(DialogProperties props,
                        SystemPromptFactory promptFactory,
                        TtsProperties ttsProps,
                        TtsRouter ttsRouter,
                        CallRecordService records,
                        VoiceMetrics metrics,
                        ObjectProvider<ChatModel> chatModelProvider,
                        LiveBroadcastService broadcast) {
        this.props = props;
        this.promptFactory = promptFactory;
        this.ttsProps = ttsProps;
        this.ttsRouter = ttsRouter;
        this.records = records;
        this.metrics = metrics;
        this.chatModelProvider = chatModelProvider;
        this.broadcast = broadcast;
    }

    @PostConstruct
    public void init() {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            chatClient = ChatClient.create(model);
            log.info("Dialog engine ready (LLM model bean: {})", model.getClass().getSimpleName());
        } else {
            log.warn("Dialog engine has no LLM ChatModel (set GEMINI_API_KEY); dialog disabled");
        }
    }

    public boolean available() {
        return props.enabled() && chatClient != null;
    }

    /**
     * Registers a conversation for {@code channelId} and drives the opening greeting.
     * No-op if dialog is disabled or the LLM is unavailable.
     *
     * @param ttsVoice catalog id of the voice this call speaks with (§2.5), or null for
     *                 the configured routing
     * @param hangup invoked once the conversation ends, to hang up the channel
     */
    public void startCall(String channelId, RtpEndpoint endpoint, CallContext context,
                          String language, String ttsVoice, Runnable hangup, Runnable transfer,
                          long callAttemptId) {
        if (!available()) {
            log.debug("Dialog not started for {} (engine unavailable)", channelId);
            return;
        }
        DialogSession session = new DialogSession(channelId, language, ttsVoice, context, endpoint,
                hangup, transfer, callAttemptId, newWatchdog());
        sessions.put(channelId, session);
        log.info("Dialog started [{}] lang={} voice={} state={}",
                channelId, language, ttsVoice != null ? ttsVoice : "default", session.state());
        startWatchdog(session);
        worker.submit(() -> {
            if (!awaitCallerReady(session)) {
                return;
            }
            speakDisclosure(session);
            advance(session, GREETING_BOOTSTRAP);
        });
    }

    /**
     * Hold the first word back until the caller can actually hear it. "Answered" means
     * the far end sent a 200 OK — the handset still needs a few hundred milliseconds to
     * open its audio path, and the person is still lifting it to their ear. Greeting
     * into that gap loses the front of the sentence: the caller hears "…alaykum" and
     * the "assalom" that carried it was spoken to nobody.
     *
     * @return false when the call ended (or the wait was interrupted) during the pause,
     *         meaning nothing should be spoken
     */
    private boolean awaitCallerReady(DialogSession s) {
        if (props.greetingDelayMs() <= 0) {
            return true;
        }
        try {
            Thread.sleep(props.greetingDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !s.isEnded();
    }

    /** A silence watchdog for a new call, or {@code null} when it is switched off. */
    private NoInputWatchdog newWatchdog() {
        if (props.noInputSeconds() <= 0) {
            return null;
        }
        return new NoInputWatchdog(Duration.ofSeconds(props.noInputSeconds()),
                props.noInputMaxPrompts(), Instant.now());
    }

    private void startWatchdog(DialogSession s) {
        if (s.watchdog() == null) {
            return;
        }
        ScheduledFuture<?> task = watchdogScheduler.scheduleWithFixedDelay(
                () -> tickWatchdog(s), WATCHDOG_TICK.toMillis(), WATCHDOG_TICK.toMillis(),
                TimeUnit.MILLISECONDS);
        s.setWatchdogTask(task);
    }

    /**
     * One watchdog tick. Runs on the scheduler thread, so it only decides — the prompt
     * (TTS) and the close (which waits for audio to drain) go to the worker.
     */
    private void tickWatchdog(DialogSession s) {
        try {
            if (s.isEnded()) {
                return;
            }
            NoInputAction action = s.watchdog()
                    .check(Instant.now(), s.endpoint().isPlaying(), s.busy().get());
            switch (action) {
                case NONE -> {
                }
                case PROMPT -> worker.submit(() -> promptForInput(s));
                case END -> worker.submit(() -> closeOnSilence(s));
            }
        } catch (Exception e) {
            log.warn("[{}] watchdog tick failed: {}", s.channelId(), e.getMessage());
        }
    }

    /**
     * Ask whether the caller is still there. Spoken from code rather than through the
     * model: there is no new client input to answer, so a turn would only ask the LLM to
     * invent something, and the point is to break the silence quickly.
     */
    private void promptForInput(DialogSession s) {
        if (s.isEnded() || !s.busy().compareAndSet(false, true)) {
            return; // a real turn started in the meantime — it will speak anyway
        }
        try {
            MDC.put("channelId", s.channelId());
            // A barge-in that was never followed by speech leaves the cancel flag set,
            // and that flag would silently swallow this prompt — the one moment the
            // caller most needs to hear something.
            s.setCancelled(false);
            String line = DialogPhrases.stillThere(s.language());
            log.info("[{}] no input for {}s — prompting ({}/{})", s.channelId(),
                    props.noInputSeconds(), s.watchdog().prompts(), props.noInputMaxPrompts());
            if (speakChunk(s, line)) {
                // Recorded in the history so the model can see it asked, and in the
                // transcript so the summary reflects a caller who went quiet.
                s.history().add(new AssistantMessage(line));
                s.setLastAgentText(line);
                recordAgentLine(s, line);
            }
            metrics.noInputPrompt();
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }

    /** Give up on a call nobody is speaking on: say goodbye and hang up. */
    private void closeOnSilence(DialogSession s) {
        if (s.isEnded()) {
            return;
        }
        log.info("[{}] closing dialog (no input after {} prompt(s))",
                s.channelId(), s.watchdog().prompts());
        // HUNG_UP rather than NO_ANSWER: the call was answered, so the number works and
        // the target is worth retrying — but nothing was agreed.
        s.end(Disposition.HUNG_UP);
        metrics.noInputHangup();
        if (!s.busy().compareAndSet(false, true)) {
            // A turn claimed the session between the check and here. It sees isEnded()
            // and finishes the call itself once it has spoken.
            return;
        }
        try {
            MDC.put("channelId", s.channelId());
            s.setCancelled(false);
            speakChunk(s, farewellLine(s));
            finishWhenSpoken(s);
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }

    /**
     * Speak the mandatory disclosure — this is an automated system and the call is
     * being recorded (§11.1) — before the model gets a turn.
     *
     * <p>The requirement is legal, and the system prompt only <em>asks</em> the model
     * to say it; a model that skips or paraphrases it away puts the call on the wrong
     * side of §11. Saying it from code makes it unconditional, and the prompt then
     * tells the model not to repeat it.
     */
    private void speakDisclosure(DialogSession s) {
        if (!props.mandatoryDisclosure()) {
            return;
        }
        String line = disclosureLine(s);
        speakChunk(s, line);
        s.setDisclosureSpoken(true);
        recordAgentLine(s, line);
        log.info("[{}] disclosure: {}", s.channelId(), line);
    }

    /** The §11.1 disclosure in the caller's language. */
    private static String disclosureLine(DialogSession s) {
        return DialogPhrases.disclosure(s.language());
    }

    /** Feeds a final client transcript into the conversation as the next turn. */
    public void onClientFinal(String channelId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        broadcast.publish(LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(channelId, "CLIENT", text, session.state().name()));
        // The client has stopped talking — the <1s turnaround budget starts here (§1.3).
        session.startTurnClock();
        session.touchActivity();
        worker.submit(() -> advance(session, text));
    }

    /**
     * Barge-in (PROJECT.md §7.2): the client started speaking. If the bot is mid-
     * utterance, silence it immediately (flush the RTP playback) and flag the turn so
     * the next LLM prompt knows it was interrupted. No-op if the bot is not speaking.
     */
    public void notifyBargeIn(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return;
        }
        // Someone is talking, so the line is not silent — even if the recognizer never
        // turns it into a final. Without this the watchdog would prompt over a caller
        // whose speech simply failed to transcribe.
        session.touchActivity();
        if (session.endpoint().isPlaying()) {
            session.endpoint().flushPlayback();
            // Also stop the turn that is still producing audio: with sentence
            // streaming the reply is not finished yet, and synthesizing the rest
            // would only queue speech the client has already talked over.
            session.setCancelled(true);
            session.setInterrupted(true);
            log.info("[{}] barge-in: bot silenced mid-utterance", channelId);
        }
    }

    /**
     * What the dialog recorded for {@code channelId}. Read before {@link #endCall}
     * drops the session — afterwards the outcome is unrecoverable.
     */
    public DialogOutcome outcome(String channelId) {
        DialogSession session = sessions.get(channelId);
        return session != null
                ? new DialogOutcome(session.disposition(), session.doNotCallReason())
                : DialogOutcome.NONE;
    }

    /**
     * What the dialog accumulated for {@code channelId}'s "Texnik" tab (§10.5). Read
     * before {@link #endCall} drops the session, same as {@link #outcome}.
     */
    public DialogTechnicalSnapshot technicalSnapshot(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null) {
            return DialogTechnicalSnapshot.NONE;
        }
        return new DialogTechnicalSnapshot(
                session.turnCount(),
                session.promptTokens(), session.completionTokens(), session.cachedTokens(),
                session.avgTurnLatencyMs(), session.maxTurnLatencyMs(),
                session.avgLlmLatencyMs(), session.maxLlmLatencyMs(),
                session.ttsVoice(), session.language());
    }

    /**
     * Marks a call as an answering machine (PROJECT.md §8.6) and stops the conversation.
     *
     * <p>Called from the AMD detector while the bot is still on its opening line. The
     * disposition has to be recorded on the session, because that is what teardown reads
     * to set {@code call_attempt.disposition} and to decide the target's retry — a call
     * that only got hung up would come back as an ordinary unanswered attempt.
     *
     * @return whether a live conversation was actually ended here
     */
    public boolean notifyVoicemail(String channelId) {
        DialogSession session = sessions.get(channelId);
        if (session == null || session.isEnded()) {
            return false;
        }
        // Silence the greeting mid-word: there is nobody to hear the rest, and every
        // sentence still queued is TTS that has already been paid for.
        session.setCancelled(true);
        session.endpoint().flushPlayback();
        session.end(Disposition.VOICEMAIL);
        log.info("[{}] answering machine detected — ending call as VOICEMAIL", channelId);
        return true;
    }

    /** Drops the conversation state when the call tears down. */
    public void endCall(String channelId) {
        DialogSession session = sessions.remove(channelId);
        if (session == null) {
            return;
        }
        ScheduledFuture<?> task = session.watchdogTask();
        if (task != null) {
            task.cancel(false);
        }
        if (session.factViolations() > 0) {
            // Worth a line at call level: a single blocked sentence is a model slip, a
            // handful in one call means the prompt or the facts are wrong.
            log.warn("[{}] fact guard blocked {} sentence(s) this call",
                    channelId, session.factViolations());
        }
    }

    /**
     * Persists one agent-spoken line and publishes it to any live-transcript
     * subscribers (§11.8) in one place, since every call site needs both and
     * {@link uz.murodjon.uysotvoice.callrecord.service.CallRecordService#addTranscript} has
     * no {@code channelId} to key a live event on — only {@code s} does.
     */
    private void recordAgentLine(DialogSession s, String line) {
        records.addTranscript(s.callAttemptId(), "AGENT", line, s.state().name(), offsetMs(s), null);
        broadcast.publish(LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(s.channelId(), "AGENT", line, s.state().name()));
    }

    private void advance(DialogSession s, String clientText) {
        if (s.isEnded()) {
            return;
        }
        // Serialize turns. Speech that lands mid-turn is held rather than dropped —
        // an LLM turn takes a second or two, and dropping it loses whole client
        // utterances, after which the bot answers a question nobody asked (§7.2).
        if (!s.busy().compareAndSet(false, true)) {
            log.debug("[{}] turn in progress, deferring: {}", s.channelId(), clientText);
            s.deferInput(clientText);
            return;
        }
        try {
            MDC.put("channelId", s.channelId());
            if (Duration.between(s.startedAt(), Instant.now()).getSeconds() > props.maxCallSeconds()) {
                closeOnLimit(s, "maksimal davomiylik");
                return;
            }
            if (s.incrementTurn() > props.maxTurns()) {
                closeOnLimit(s, "maksimal turn soni");
                return;
            }
            // Cost cap (§C14). The turn and duration caps bound a call that behaves;
            // this one bounds a call that does not — a model stuck in a loop resends the
            // whole context every turn, and the bill grows even while the turn count
            // looks reasonable.
            if (props.maxTokensPerCall() > 0 && s.tokensUsed() >= props.maxTokensPerCall()) {
                closeOnLimit(s, "token budjeti (" + s.tokensUsed() + " token)");
                return;
            }

            s.history().add(new UserMessage(clientText));
            int dropped = s.trimHistory(props.historyMaxMessages(), HISTORY_TRIM_BLOCK);
            if (dropped > 0) {
                log.debug("[{}] history trimmed by {} messages (cap {})",
                        s.channelId(), dropped, props.historyMaxMessages());
            }

            String system = systemPrefix(s);
            // The state block goes after the history, not into the system message, so
            // the cached prefix stays append-only (see SystemPromptFactory).
            List<Message> messages = new ArrayList<>(s.history());
            messages.add(new UserMessage(promptFactory.turnAnnex(s)));
            s.setInterrupted(false); // the annex has carried the barge-in note now

            List<ToolCallback> tools = toolsFor(s);
            s.setCancelled(false); // a previous turn's barge-in must not mute this one

            TurnResult result;
            Timer.Sample llmSample = metrics.startTimer();
            try {
                result = props.streaming()
                        ? streamTurn(s, system, messages, tools)
                        : blockingTurn(s, system, messages, tools);
            } catch (Exception e) {
                metrics.llmError();
                log.warn("LLM turn failed [{}]: {}", s.channelId(), e.getMessage());
                // Fall through with no reply rather than returning: the fallback below
                // still has to speak. Returning here left the caller listening to
                // silence, and in GREETING that is the whole call — the bot never
                // says anything at all.
                result = TurnResult.NOTHING;
            } finally {
                long elapsedNanos = metrics.stopLlmTurn(llmSample);
                s.recordLlmLatency(Duration.ofNanos(elapsedNanos).toMillis());
            }

            String reply = result.reply();
            boolean haveText = reply != null && !reply.isBlank();
            if (haveText) {
                s.history().add(new AssistantMessage(reply));
                s.setLastAgentText(reply); // remembered so barge-in can tell the model where it stopped
                log.info("[{}] AGENT ({}): {}", s.channelId(), s.state(), reply);
                recordAgentLine(s, reply);
            }
            if (result.toolNote() != null && result.toolNote().contains("XATO")) {
                // The model spoke and called a tool in the same breath, and the tool
                // refused it (§4.4 — a promise dated in the past, say). There was no
                // retry to show it the rejection, so the next turn carries it instead;
                // otherwise the model believes a promise that was never recorded.
                s.history().add(new UserMessage("[TIZIM: Tool natijasi: " + result.toolNote() + "]"));
            }

            if (result.blocked() && !result.spoken()) {
                // The whole reply was a figure the caller must not be told. Correct it
                // once, then hand over to a person — never guess a sum aloud (§4.4).
                recoverFromFactBlock(s, system, messages);
            } else if (!result.spoken() && !s.isEnded()) {
                // Nothing reached the caller and the call is not over: speak a neutral
                // line rather than hand them silence.
                if (!haveText) {
                    metrics.llmError();
                    log.warn("[{}] no speakable text from the LLM in {}; using the fallback line",
                            s.channelId(), s.state());
                }
                speakChunk(s, fallbackLine(s));
            }
            if (s.isEnded()) {
                finishWhenSpoken(s);
            }
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
            // Anything the client said during this turn becomes the next one. Submitted
            // after busy is cleared so the new turn can actually claim the session.
            String deferred = s.takeDeferredInput();
            if (deferred != null && !deferred.isBlank() && !s.isEnded()) {
                worker.submit(() -> advance(s, deferred));
            }
        }
    }

    /**
     * One turn, streamed: sentences are synthesized and queued as the model produces
     * them, so the caller hears the opening words while the rest is still generating.
     * This is where the turnaround budget is won — the alternative serializes the full
     * LLM latency and the full synthesis latency before any audio exists (§7.2, §1.3).
     *
     * @return the reply text plus whether any of it actually reached the caller
     */
    private TurnResult streamTurn(DialogSession s, String system, List<Message> messages, List<ToolCallback> tools) {
        TokenUsage turnUsage = new TokenUsage();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        StreamedReply reply = consume(s, chatClient.prompt()
                .system(system)
                .messages(messages)
                .options(manualToolOptions(tools))
                .stream()
                .chatResponse(), turnUsage, toolCalls);
        publishUsage(s, turnUsage);
        String toolNote = runTools(s, tools, toolCalls);

        if (!reply.text().isEmpty()) {
            return new TurnResult(reply.text(), reply.spoken(), reply.blocked(), toolNote);
        }
        // Tool-only turn: the model called e.g. transitionTo and produced no text, which
        // would leave the caller listening to silence. The tools have already run, so ask
        // once more for the spoken line alone — streamed like any other turn, because
        // this is the common shape of a state transition and it is on the clock (§1.3).
        log.warn("[{}] empty LLM reply in {} — asking again for the spoken line",
                s.channelId(), s.state());
        TokenUsage retryUsage = new TokenUsage();
        StreamedReply retry = consume(s, chatClient.prompt()
                .system(system)
                .messages(spokenLineRetry(messages, toolNote))
                .stream()
                .chatResponse(), retryUsage, new ArrayList<>());
        publishUsage(s, retryUsage);
        // toolNote dropped: the retry has already shown it to the model.
        return new TurnResult(retry.text(), retry.spoken(), retry.blocked(), null);
    }

    /**
     * Drain one streamed reply: accumulate its text, collect the tool calls it carries,
     * and speak each sentence as soon as it is complete.
     *
     * <p>The stream is drained to completion even after a barge-in — abandoning the
     * iterator mid-way leaves the subscription open. Cancellation stops the synthesis,
     * which is the part that costs money and airtime.
     */
    private StreamedReply consume(DialogSession s, Flux<ChatResponse> responses,
                                  TokenUsage usage, List<AssistantMessage.ToolCall> toolCalls) {
        StringBuilder full = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        boolean spoken = false;
        boolean blocked = false;

        for (ChatResponse response : responses.toIterable()) {
            usage.add(response);
            // Collected, not run yet: a tool may end the call, and the sentences already
            // in flight should still be spoken before that takes effect.
            toolCalls.addAll(toolCallsOf(response));
            String chunk = textOf(response);
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            full.append(chunk);
            if (s.isCancelled()) {
                continue;
            }
            pending.append(chunk);
            for (String sentence = takeSentence(pending); sentence != null; sentence = takeSentence(pending)) {
                SpeechOutcome outcome = speak(s, sentence);
                spoken |= outcome == SpeechOutcome.SPOKEN;
                blocked |= outcome == SpeechOutcome.BLOCKED;
            }
        }
        if (!s.isCancelled() && !pending.isEmpty()) {
            SpeechOutcome outcome = speak(s, pending.toString().trim());
            spoken |= outcome == SpeechOutcome.SPOKEN;
            blocked |= outcome == SpeechOutcome.BLOCKED;
        }
        return new StreamedReply(full.toString().trim(), spoken, blocked);
    }

    /** What one drained stream produced — the same three facts a {@link TurnResult} carries. */
    private record StreamedReply(String text, boolean spoken, boolean blocked) {
    }

    /** One turn in a single blocking call — the pre-streaming path, kept as a fallback. */
    private TurnResult blockingTurn(DialogSession s, String system, List<Message> messages,
                                    List<ToolCallback> tools) {
        ChatResponse response = chatClient.prompt()
                .system(system)
                .messages(messages)
                .options(manualToolOptions(tools))
                .call()
                .chatResponse();
        TokenUsage turnUsage = new TokenUsage();
        turnUsage.add(response);
        publishUsage(s, turnUsage);
        String toolNote = runTools(s, tools, toolCallsOf(response));

        String reply = textOf(response);
        if (reply == null || reply.isBlank()) {
            log.warn("[{}] empty LLM reply in {} — asking again for the spoken line",
                    s.channelId(), s.state());
            reply = retryForSpokenLine(s, system, messages, toolNote);
            toolNote = null; // the retry has already shown it to the model
        }
        SpeechOutcome outcome = speak(s, reply);
        return new TurnResult(reply, outcome == SpeechOutcome.SPOKEN,
                outcome == SpeechOutcome.BLOCKED, toolNote);
    }

    /** Second attempt when a turn produced only tool calls: text, no tools. */
    private String retryForSpokenLine(DialogSession s, String system, List<Message> messages, String toolNote) {
        ChatResponse response = chatClient.prompt()
                .system(system)
                .messages(spokenLineRetry(messages, toolNote))
                .call()
                .chatResponse();
        TokenUsage retryUsage = new TokenUsage();
        retryUsage.add(response);
        publishUsage(s, retryUsage);
        return textOf(response);
    }

    /**
     * The turn's messages plus the instruction to answer with the spoken line alone.
     *
     * <p>Appended rather than folded into the system message: changing the system
     * message would invalidate the cached prefix this whole design protects, and this
     * extra round trip is exactly the one worth keeping cheap.
     *
     * @param toolNote what the tools returned, so the model can react to a guardrail
     *                 that refused it (a promise dated in the past) — or null
     */
    private static List<Message> spokenLineRetry(List<Message> messages, String toolNote) {
        List<Message> retryMessages = new ArrayList<>(messages);
        if (toolNote != null) {
            retryMessages.add(new UserMessage("[TIZIM: Tool natijasi: " + toolNote + "]"));
        }
        retryMessages.add(new UserMessage("[TIZIM: Endi faqat mijozga ovoz bilan aytiladigan "
                + "matnni qaytaring. Tool chaqirmang, izoh yozmang.]"));
        return retryMessages;
    }

    /**
     * Recover from a reply the fact guard refused in full (§4.4).
     *
     * <p>Naming the mistake and asking again is usually enough — the model had the right
     * figure in its prompt all along. If the second attempt is wrong too, the call goes
     * to a person: a caller may be left waiting, but never told a sum that is not theirs.
     */
    private void recoverFromFactBlock(DialogSession s, String system, List<Message> messages) {
        List<Message> retryMessages = new ArrayList<>(messages);
        retryMessages.add(new UserMessage("[TIZIM: Oxirgi javobingizda kontekstda berilmagan pul "
                + "summasi bor edi, shuning uchun u mijozga AYTILMADI. Javobni qaytadan yozing va "
                + "faqat yuqorida berilgan qarz summasini ishlating. Tool chaqirmang.]"));
        String second = null;
        try {
            ChatResponse response = chatClient.prompt()
                    .system(system)
                    .messages(retryMessages)
                    .call()
                    .chatResponse();
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            publishUsage(s, usage);
            second = textOf(response);
        } catch (Exception e) {
            log.warn("[{}] fact-guard retry failed: {}", s.channelId(), e.getMessage());
        }
        if (second != null && !second.isBlank() && speak(s, second) == SpeechOutcome.SPOKEN) {
            s.history().add(new AssistantMessage(second));
            s.setLastAgentText(second);
            recordAgentLine(s, second);
            log.info("[{}] AGENT ({}, fact-guard retry): {}", s.channelId(), s.state(), second);
            return;
        }
        log.error("[{}] fact guard blocked the reply twice in {} — escalating to an operator",
                s.channelId(), s.state());
        s.end(Disposition.TRANSFERRED);
        speakChunk(s, DialogPhrases.transferring(s.language()));
    }

    /** Add a turn's tokens to the call total (for the budget) and publish the metrics. */
    private void publishUsage(DialogSession s, TokenUsage usage) {
        usage.publish(s, metrics);
        s.addTokens(usage.total());
    }

    /** The stable, per-call half of the prompt — built once and then reused verbatim. */
    private String systemPrefix(DialogSession s) {
        String prefix = s.systemPrefix();
        if (prefix == null) {
            prefix = promptFactory.stablePrefix(s);
            s.setSystemPrefix(prefix);
        }
        return prefix;
    }

    /**
     * The tools this state may use. Every tool declaration is re-sent (and re-billed)
     * on every turn, and the ones that cannot legitimately fire yet are also the ones a
     * model is most likely to misfire: nothing can be promised before the debt has been
     * named, and nobody is the wrong person once they have confirmed who they are.
     *
     * <p>The set only changes on a state transition, so the request prefix — which
     * includes the tool declarations — still holds still for several turns at a time.
     * Set {@code voice-agent.dialog.state-scoped-tools=false} to send them all.
     */
    private List<ToolCallback> toolsFor(DialogSession s) {
        ToolCallback[] all = MethodToolCallbackProvider.builder()
                .toolObjects(new DialogTools(s))
                .build()
                .getToolCallbacks();
        if (!props.stateScopedTools()) {
            return List.of(all);
        }
        Set<String> allowed = allowedTools(s.state());
        return Arrays.stream(all)
                .filter(callback -> allowed.contains(callback.getToolDefinition().name()))
                .toList();
    }

    private static Set<String> allowedTools(DialogState state) {
        return switch (state) {
            // Before the debt has been stated, the only outcomes on the table are "this
            // is the wrong person" and the always-available escalations.
            case GREETING, IDENTITY_CHECK -> union("recordWrongPerson");
            // The client may volunteer a date or a reason as soon as they hear the
            // amount, so both recording tools are live from here on.
            case DEBT_NOTICE -> union("recordWrongPerson", "recordPaymentPromise", "recordRefusalReason");
            case REASON_INQUIRY, PAYMENT_DATE, CONFIRMATION ->
                    union("recordPaymentPromise", "recordRefusalReason");
            // Closing and the terminal states only need to say goodbye and hang up.
            case CLOSING, ESCALATE_TO_HUMAN, END_CALL -> ALWAYS_AVAILABLE_TOOLS;
        };
    }

    private static Set<String> union(String... extra) {
        Set<String> names = new HashSet<>(ALWAYS_AVAILABLE_TOOLS);
        names.addAll(Arrays.asList(extra));
        return names;
    }

    /**
     * This turn's tools, with Spring AI's built-in tool-calling loop switched off —
     * {@link #runTools} executes them here instead.
     *
     * <p>That loop answers a tool call by replaying the model's own {@code functionCall}
     * back to it alongside the result, and Gemini 3 rejects the replay unless every
     * function call carries the opaque {@code thought_signature} it was issued with
     * (HTTP 400, "Function call is missing a thought_signature in functionCall parts").
     * Getting those signatures out of the provider means echoing the model's thinking
     * back verbatim — which this engine cannot do, because the same parsing path would
     * hand that reasoning to the TTS and speak it to the caller.
     *
     * <p>Running the tools here removes the round trip that needs a signature at all.
     * Nothing is lost: the tools move the FSM and record outcomes, their return strings
     * are confirmations, and the conversation history was already text-only. It is also
     * one LLM call cheaper per tool-calling turn, which the &lt;1s budget (§1.3) notices.
     *
     * <p>A fresh options object every turn — the ChatClient merges the tool callbacks
     * into the instance it is handed, so a shared one would accumulate them.
     */
    private static ChatOptions manualToolOptions(List<ToolCallback> tools) {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .internalToolExecutionEnabled(false)
                .build();
    }

    /** Every tool call carried by one response (or one streamed chunk of it). */
    private static List<AssistantMessage.ToolCall> toolCallsOf(ChatResponse response) {
        if (response == null) {
            return List.of();
        }
        return response.getResults().stream()
                .map(Generation::getOutput)
                .flatMap(output -> output.getToolCalls().stream())
                .toList();
    }

    /**
     * Run the tools the model asked for, in the order it asked for them.
     *
     * <p>A failure is logged and reported rather than thrown: a tool that did not run
     * is a lost outcome, but an exception here would cost the caller the whole reply.
     *
     * @return what the tools returned, for the model to see — or null if it called none
     */
    private String runTools(DialogSession s, List<ToolCallback> tools,
                            List<AssistantMessage.ToolCall> calls) {
        if (calls.isEmpty()) {
            return null;
        }
        List<String> results = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            ToolCallback callback = tools.stream()
                    .filter(t -> t.getToolDefinition().name().equals(call.name()))
                    .findFirst()
                    .orElse(null);
            if (callback == null) {
                // A tool this state does not offer (see toolsFor) — the model invented
                // the name, or is reaching for an outcome that is not on the table yet.
                log.warn("[{}] model called unavailable tool {} in {}",
                        s.channelId(), call.name(), s.state());
                results.add("XATO: " + call.name() + " hozirgi bosqichda mavjud emas");
                continue;
            }
            try {
                String result = callback.call(call.arguments());
                log.debug("[{}] tool {}({}) -> {}", s.channelId(), call.name(), call.arguments(), result);
                if (!result.isBlank()) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.warn("[{}] tool {} failed: {}", s.channelId(), call.name(), e.getMessage());
                results.add("XATO: " + call.name() + " bajarilmadi");
            }
        }
        return results.isEmpty() ? null : String.join(" ", results);
    }

    /** The assistant text carried by one response (or chunk), or {@code null}. */
    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * Cut the next complete sentence off the front of {@code pending}, or return
     * {@code null} if there is not one yet.
     *
     * <p>A minimum length keeps the model's own abbreviations and decimals from being
     * cut into fragments too small to synthesize naturally — a two-word "sentence"
     * sounds clipped, and each cut costs a separate TTS round trip.
     */
    private static String takeSentence(StringBuilder pending) {
        for (int i = MIN_SENTENCE_CHARS - 1; i < pending.length(); i++) {
            char c = pending.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n' || c == '…') {
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
        }
        return null;
    }

    /**
     * Last resort when the LLM returns no text at all: the caller must hear something.
     * In GREETING that has to be the opening line (nobody has spoken yet); later on,
     * asking the client to repeat keeps the conversation alive.
     */
    private static String fallbackLine(DialogSession s) {
        if (s.state() == DialogState.GREETING && !s.isDisclosureSpoken()) {
            return disclosureLine(s);
        }
        return DialogPhrases.didNotCatch(s.language());
    }

    /** Closing line when a guardrail ends the call, in the caller's language. */
    private static String farewellLine(DialogSession s) {
        return DialogPhrases.farewell(s.language());
    }

    /** What became of one piece of speech handed to {@link #speak}. */
    private enum SpeechOutcome {
        /** Audio was synthesized and queued for the caller. */
        SPOKEN,
        /** The fact guard refused it — it stated a figure that is not in the facts. */
        BLOCKED,
        /** Nothing to say, TTS is off, TTS failed, or a barge-in overtook it. */
        SKIPPED
    }

    /**
     * One turn's outcome: the model's text, whether any of it reached the caller,
     * whether the fact guard is the reason it did not, and anything the tools said back
     * that the model has not been shown yet ({@code toolNote}, usually null).
     *
     * <p>Text and audio are tracked separately because they diverge in exactly the cases
     * that matter — a guard block or a TTS failure produces a perfectly good reply that
     * the caller never heard, and the caller's experience is silence.
     */
    private record TurnResult(String reply, boolean spoken, boolean blocked, String toolNote) {

        static final TurnResult NOTHING = new TurnResult(null, false, false, null);
    }

    /**
     * Synthesize one piece of the reply and queue it behind whatever is already playing.
     * Called once per sentence while streaming, or once for the whole reply otherwise.
     * Failures are logged, never thrown: a lost sentence is better than an aborted turn.
     */
    private SpeechOutcome speak(DialogSession s, String text) {
        if (!ttsProps.enabled() || text == null || text.isBlank()) {
            return SpeechOutcome.SKIPPED;
        }
        if (props.factGuard()) {
            List<String> bad = FactGuard.violations(text, s.context());
            if (!bad.isEmpty()) {
                // Not spoken, not recorded as said: the caller must never hear a sum
                // that is not in their file (§4.4). The caller-facing recovery is the
                // caller's, not this method's — see recoverFromFactBlock.
                s.recordFactViolation();
                metrics.factGuardBlock();
                log.error("[{}] fact guard BLOCKED a sentence in {}: figures {} are not in the "
                                + "call facts (debt={}) — text was: {}",
                        s.channelId(), s.state(), bad,
                        s.context() != null ? s.context().debtAmount() : null, text);
                return SpeechOutcome.BLOCKED;
            }
        }
        try {
            short[] pcm = ttsRouter.synthesize(text, s.language(), s.ttsVoice());
            if (s.isCancelled()) {
                return SpeechOutcome.SKIPPED; // barge-in landed while we were synthesizing
            }
            s.endpoint().enqueuePcm(pcm);
            Duration turnaround = s.takeTurnaround();
            if (turnaround != null) {
                // First audio of this turn — the number §1.3 budgets.
                metrics.recordTurnaround(turnaround);
                s.recordTurnLatency(turnaround.toMillis());
                log.debug("[{}] turnaround {} ms", s.channelId(), turnaround.toMillis());
            }
            return SpeechOutcome.SPOKEN;
        } catch (Exception e) {
            log.warn("TTS failed during dialog [{}]: {}", s.channelId(), e.getMessage());
            return SpeechOutcome.SKIPPED;
        }
    }

    /** {@link #speak} for the fixed lines, where only "did the caller hear it" matters. */
    private boolean speakChunk(DialogSession s, String text) {
        return speak(s, text) == SpeechOutcome.SPOKEN;
    }

    /**
     * Waits for the queued audio to drain, then ends the call: transfer to a human
     * operator if the outcome is TRANSFERRED (§11.6), otherwise hang up.
     *
     * <p>Polls the endpoint rather than sleeping for a precomputed duration: with
     * sentence streaming the total length is not known when the last sentence is
     * queued, and hanging up early would cut off the goodbye.
     */
    private void finishWhenSpoken(DialogSession s) {
        long deadline = System.nanoTime() + MAX_DRAIN.toNanos();
        try {
            while (s.endpoint().isPlaying() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            Thread.sleep(500); // let the tail reach the caller before the channel drops
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Runnable action = (s.disposition() == Disposition.TRANSFERRED && s.transfer() != null)
                ? s.transfer() : s.hangup();
        if (action != null) {
            action.run();
        }
    }

    /**
     * Every call still in conversation, for the "Jonli qo'ng'iroqlar" list
     * (§10.2/§10.3 UI-DESIGN.md). Excludes a session already marked {@code ended} —
     * such a session is draining its farewell audio and tearing down, not live.
     */
    public List<LiveDialogSnapshot> liveDialogs() {
        return sessions.values().stream()
                .filter(s -> !s.isEnded())
                .map(s -> new LiveDialogSnapshot(
                        s.channelId(),
                        s.startedAt(),
                        s.state().name(),
                        s.language(),
                        s.context() != null ? s.context().clientName() : null))
                .toList();
    }

    /** Live context for the operator screen after a transfer (null if unknown). */
    public OperatorSnapshot operatorSnapshot(String channelId) {
        DialogSession s = sessions.get(channelId);
        if (s == null) {
            return null;
        }
        CallContext c = s.context();
        return new OperatorSnapshot(
                channelId,
                c.clientName(),
                c.debtAmount() != null ? c.debtAmount().toString() : null,
                c.currency(),
                c.dueDate() != null ? c.dueDate().toString() : null,
                c.contractNumber(),
                s.state().name(),
                records.transcriptText(s.callAttemptId()));
    }

    private static int offsetMs(DialogSession s) {
        return (int) Duration.between(s.startedAt(), Instant.now()).toMillis();
    }

    private void closeOnLimit(DialogSession s, String reason) {
        log.info("[{}] closing dialog ({})", s.channelId(), reason);
        s.end(null);
        speakChunk(s, farewellLine(s));
        finishWhenSpoken(s);
    }

    @PreDestroy
    public void shutdown() {
        watchdogScheduler.shutdownNow();
        worker.shutdownNow();
    }
}
