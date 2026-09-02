package uz.murodjon.robotcallv2.agent.dialog;

import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * One turn of the conversation, end to end (PROJECT.md §4, §7.1): take what the caller
 * said, ask the LLM, run whatever tools it called, and hand the sentences to
 * {@link SpeechOutput} as they arrive. This is the middle box of the pipeline — the
 * recognizer's words come in from {@link ClientInputGate}, the reply goes out through
 * the TTS.
 *
 * <p>The request is assembled to stay cacheable: system prefix, then the history, then
 * everything that changes per turn. Providers price a repeated prefix at a fraction of
 * a fresh one, and a 25-turn call resends its whole context every turn — see
 * {@link SystemPromptFactory}. {@code voice.llm.tokens.cached} is where that shows up.
 *
 * <p>Turns run on the virtual-thread worker so the blocking LLM/TTS calls never stall
 * the STT/RTP threads. The session's {@code busy} flag serializes them and holds a final
 * that arrives while a turn is still in flight.
 *
 * <p>Owns the call's {@link ChatClient}, and therefore whether dialog is possible at all:
 * without an LLM ChatModel bean (no {@code GEMINI_API_KEY}) the app still starts and
 * {@link #available()} is false.
 */
@Component
public class TurnRunner {

    private static final Logger log = LoggerFactory.getLogger(TurnRunner.class);

    /** Bootstraps the opening turn — the bot speaks first (there is no client input yet). */
    private static final String GREETING_BOOTSTRAP =
            "[TIZIM: Qo'ng'iroq ulandi, mijoz go'shakni ko'tardi. Rejaga muvofiq salomlashing.]";

    /**
     * How many messages to drop at once when the history cap is reached. Trimming in
     * a block rather than one per turn keeps the request prefix stable for longer,
     * which is what the provider's context cache is keyed on.
     */
    private static final int HISTORY_TRIM_BLOCK = 6;

    private final DialogProperties props;
    private final SystemPromptFactory promptFactory;
    private final VoiceMetrics metrics;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final TurnTools turnTools;
    private final SpeechOutput speech;
    private final DialogTranscript transcript;
    private final DialogExecutors executors;
    private final SentimentDetector sentimentDetector;

    private volatile ChatClient chatClient;

    public TurnRunner(DialogProperties props,
                      SystemPromptFactory promptFactory,
                      VoiceMetrics metrics,
                      ObjectProvider<ChatModel> chatModelProvider,
                      TurnTools turnTools,
                      SpeechOutput speech,
                      DialogTranscript transcript,
                      DialogExecutors executors,
                      SentimentDetector sentimentDetector) {
        this.props = props;
        this.promptFactory = promptFactory;
        this.metrics = metrics;
        this.chatModelProvider = chatModelProvider;
        this.turnTools = turnTools;
        this.speech = speech;
        this.transcript = transcript;
        this.executors = executors;
        this.sentimentDetector = sentimentDetector;
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

    /** Whether a turn could run at all right now. */
    public boolean available() {
        return props.enabled() && chatClient != null;
    }

    /** The opening turn: nobody has spoken yet, so the bootstrap line stands in for the caller. */
    public void startGreeting(DialogSession s) {
        advance(s, GREETING_BOOTSTRAP, false);
    }

    /**
     * @param fromClient whether {@code clientText} is something the caller actually said,
     *                   as opposed to the greeting bootstrap. Only a caller is waiting on
     *                   an answer, so only a caller's turn runs the §1.3 turnaround clock
     *                   — and that clock is also what arms the filler and the streamed
     *                   first sentence, neither of which belongs on the opening greeting
     */
    public void advance(DialogSession s, String clientText, boolean fromClient) {
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
            if (fromClient) {
                // The client has stopped talking — the <1s turnaround budget starts here
                // (§1.3). Started once the turn is genuinely under way, not when the final
                // arrived: a final that lands mid-turn is deferred, and restarting the
                // clock from there used to reset the running turn's own measurement and
                // hand the rest of its sentences to the streaming path meant for the first.
                s.startTurnClock();
                if (clientText != null && !clientText.isBlank()) {
                    s.setLastCustomerSentiment(sentimentDetector.analyze(clientText));
                }
            }
            // Cleared before the caps below, not after they have been checked: those close
            // the call with a spoken farewell, and a previous turn's barge-in flag left
            // standing would swallow it — the caller's last experience of the call would
            // be the line going dead.
            s.setCancelled(false);
            if (Duration.between(s.startedAt(), Instant.now()).getSeconds() > s.aiModel().maxCallSeconds()) {
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
            if (s.aiModel().maxTokensPerCall() > 0 && s.tokensUsed() >= s.aiModel().maxTokensPerCall()) {
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

            List<ToolCallback> tools = turnTools.build(s);
            s.clearToolReplies(); // the last turn's lines have been spoken or dropped
            s.clearSpokenText();  // this turn's own account of what the caller hears
            s.setUnspokenText(null); // a previous turn's cut-off tail is stale now

            // A reply that was already being written while the caller finished speaking,
            // if the final turned out to say what the interim did. Taken before the timer
            // starts on purpose: the LLM latency this turn is charged with should be the
            // part the caller actually waited through.
            Flux<ChatResponse> speculated = adoptSpeculation(s, clientText, fromClient);

            TurnResult result;
            Timer.Sample llmSample = metrics.startTimer();
            ScheduledFuture<?> filler = speech.scheduleFiller(s);
            try {
                result = props.streaming()
                        ? streamTurn(s, system, messages, tools, speculated)
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
                if (filler != null) {
                    // The turn is over; anything still pending would land after the reply.
                    // A filler already in flight is stopped by its own guards instead.
                    filler.cancel(false);
                }
                long elapsedNanos = metrics.stopLlmTurn(llmSample);
                s.recordLlmLatency(Duration.ofNanos(elapsedNanos).toMillis());
            }

            // A barge-in landed: the history, the transcript and the "where you stopped"
            // note record what the caller HEARD, which is no longer what the model wrote.
            // Told it delivered the whole reply, the model treats the sentences the caller
            // never heard as said, and the sum and the due date never come up again.
            boolean cutOff = s.isCancelled();
            String reply = cutOff ? s.spokenText() : result.reply();
            boolean haveText = reply != null && !reply.isBlank();
            if (haveText) {
                s.history().add(new AssistantMessage(reply));
                s.setLastAgentText(reply); // remembered so barge-in can tell the model where it stopped
                log.info("[{}] AGENT ({}{}): {}", s.channelId(), s.state(),
                        cutOff ? ", cut off by the caller" : "", reply);
                transcript.recordAgentLine(s, reply);
            }
            if (result.toolNote() != null && result.toolNote().contains("XATO")) {
                // The model spoke and called a tool in the same breath, and the tool
                // refused it (§4.4 — a promise dated in the past, say). There was no
                // retry to show it the rejection, so the next turn carries it instead;
                // otherwise the model believes a promise that was never recorded.
                s.history().add(new UserMessage("[TIZIM: Tool natijasi: " + result.toolNote() + "]"));
            }

            if (cutOff) {
                // Nothing more is spoken into a caller who is talking. Neither recovery
                // below applies: both exist to break a silence, and there is none.
                log.debug("[{}] turn cut off by the caller — no recovery line", s.channelId());
            } else if (result.blocked() && !result.spoken()) {
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
                speech.speakChunk(s, DialogLines.fallback(s));
            }
            if (s.isEnded()) {
                speech.finishWhenSpoken(s);
            }
        } finally {
            boolean interrupted = s.isCancelled();
            int turn = s.turnCount();
            s.busy().set(false);
            MDC.remove("channelId");
            // Anything the client said during this turn becomes the next one. Submitted
            // after busy is cleared so the new turn can actually claim the session.
            String deferred = s.takeDeferredInput();
            if (deferred != null && !deferred.isBlank() && !s.isEnded()) {
                executors.submit(() -> advance(s, deferred, true));
            } else if (interrupted && !s.isEnded()) {
                // Silenced, but nothing came back to show for it. Give the words a moment
                // to arrive before deciding the interruption was real.
                scheduleFalseInterruptionCheck(s, turn, () -> resumeInterruptedReply(s, turn));
            }
        }
    }

    private void closeOnLimit(DialogSession s, String reason) {
        log.info("[{}] closing dialog ({})", s.channelId(), reason);
        s.end(null);
        speech.speakChunk(s, DialogLines.farewell(s));
        speech.finishWhenSpoken(s);
    }

    // ------------------------------------------------------------------
    // Speculation: writing the reply before the caller has finished saying it
    // ------------------------------------------------------------------

    /**
     * Start writing a reply to what the recognizer currently thinks the caller is saying,
     * before it has committed to it. Nothing is ever spoken from one — it starts the LLM
     * early, so the reply is already being written during the endpointing silence the
     * turn would otherwise spend waiting (§1.3, {@link Speculation}).
     *
     * <p>This is not the §7.4 warning about answering interim text. Nothing here reaches
     * the caller unless the final says the same thing; a hypothesis the caller revises is
     * cancelled and the turn runs exactly as it does today.
     */
    public void speculate(DialogSession s, String text) {
        if (!props.preemptive() || !props.streaming() || text == null) {
            return;
        }
        if (!readyToSpeculate(s, text)) {
            return;
        }
        Speculation parked = s.speculation();
        if (parked != null && TranscriptText.saysTheSame(parked.inputText(), text)) {
            return; // the same hypothesis, already being written
        }
        executors.submit(() -> startSpeculation(s, text));
    }

    /**
     * Whether this call is in a state where guessing at the reply is both possible and
     * worth the tokens.
     *
     * <p>The interesting condition is {@code isInterrupted}: the turn annex tells the
     * model where a barge-in cut it off, so a reply written before that note exists
     * answers a different prompt than the turn would send. Everything else is either a
     * turn already in progress, a call with no opening line yet, or a budget with too
     * little left to spend on a guess.
     */
    private boolean readyToSpeculate(DialogSession s, String text) {
        return !s.isEnded()
                && !s.busy().get()
                && !s.isInterrupted()
                && s.turnCount() > 0
                && text.length() >= props.preemptiveMinChars()
                && withinSpeculationBudget(s);
    }

    /**
     * Whether the call can afford a reply it may throw away. The last fifth of the budget
     * is reserved for turns that are certain to be spoken — running out of tokens on a
     * guess would close the call (§C14) over words the caller never said.
     */
    private static boolean withinSpeculationBudget(DialogSession s) {
        long cap = s.aiModel().maxTokensPerCall();
        return cap <= 0 || s.tokensUsed() < cap / 5 * 4;
    }

    /**
     * Send the speculative request.
     *
     * <p>The prompt is built practical as {@link #advance} would build it, but from a copy
     * of the history: nothing here may leave a mark on the session, because the caller
     * may yet say something else entirely.
     */
    private void startSpeculation(DialogSession s, String text) {
        if (!readyToSpeculate(s, text)) {
            return; // a turn claimed the call between the submit and here
        }
        try {
            MDC.put("channelId", s.channelId());
            int turn = s.turnCount();
            List<Message> messages = new ArrayList<>(s.history());
            if (s.turnCount() != turn || s.busy().get()) {
                // A real turn claimed the call while the history was being copied, so the
                // copy may be half of it. Nothing is lost by dropping the guess — the turn
                // that took over sends the whole history itself.
                return;
            }
            messages.add(new UserMessage(text));
            messages.add(new UserMessage(promptFactory.turnAnnex(s)));
            // cache() over an eager subscription is the whole mechanism: the request goes
            // out now and every chunk is buffered, so the turn that adopts it is replayed
            // all of them at once and then carries on with the live stream.
            Flux<ChatResponse> responses = chatClient.prompt()
                    .system(systemPrefix(s))
                    .messages(messages)
                    .options(turnTools.buildOptions(s, turnTools.build(s)))
                    .stream()
                    .chatResponse()
                    .cache();
            Disposable warmUp = responses.subscribe(
                    ignored -> { },
                    error -> log.debug("[{}] speculative reply failed: {}", s.channelId(), error.toString()));
            s.setSpeculation(new Speculation(text, responses, warmUp));
            metrics.speculationStarted();
            log.debug("[{}] speculating on: {}", s.channelId(), text);
        } catch (Exception e) {
            // A guess that cannot be started costs the turn nothing — it runs as before.
            log.debug("[{}] could not start a speculative reply: {}", s.channelId(), e.getMessage());
        } finally {
            MDC.remove("channelId");
        }
    }

    static boolean isSpeculationCompatible(String speculationText, String actualText) {
        if (speculationText == null || actualText == null) {
            return false;
        }
        String normSpec = TranscriptText.normalize(speculationText);
        String normAct = TranscriptText.normalize(actualText);
        if (normSpec.equals(normAct)) {
            return true;
        }
        return normAct.startsWith(normSpec) || normSpec.startsWith(normAct);
    }

    /**
     * The already-running reply stream for this turn, or {@code null} if there is none to
     * use. A parked reply written for different words is cancelled here: it answers a
     * sentence the caller turned out not to have said.
     */
    private Flux<ChatResponse> adoptSpeculation(DialogSession s, String clientText, boolean fromClient) {
        Speculation parked = s.takeSpeculation();
        if (parked == null) {
            return null;
        }
        if (fromClient && isSpeculationCompatible(parked.inputText(), clientText)) {
            metrics.speculationHit();
            log.debug("[{}] adopted a reply started before the caller finished", s.channelId());
            return parked.responses();
        }
        parked.discard();
        metrics.speculationMiss();
        return null;
    }

    // ------------------------------------------------------------------
    // False barge-in: deciding after the fact that nobody actually spoke
    // ------------------------------------------------------------------

    /**
     * Arm the check that decides, after the fact, whether a barge-in was a real one.
     *
     * <p>A VAD fires on a cough, a chair, a voice in the room and the bot's own audio
     * coming back off a speakerphone, exactly as it fires on the caller — and by the time
     * anything can tell the difference the bot has already been silenced, which is the
     * right order (waiting to be sure means talking over someone who really did speak).
     * What separates the two afterwards is simple: a real interruption is followed by
     * words, and words start a turn. So if the turn number has not moved by the time this
     * runs, nobody spoke, and the caller is sitting in a silence the bot created.
     */
    public void scheduleFalseInterruptionCheck(DialogSession s, int turn, Runnable onFalseInterruption) {
        if (props.falseInterruptionTimeoutMs() <= 0) {
            return;
        }
        executors.scheduleOnWorker(() -> runIfFalseInterruption(s, turn, onFalseInterruption),
                props.falseInterruptionTimeoutMs());
    }

    /** Carry out {@code onFalseInterruption} if the call is still where the barge-in left it. */
    private void runIfFalseInterruption(DialogSession s, int turn, Runnable onFalseInterruption) {
        if (s.isEnded() || s.turnCount() != turn || s.busy().get() || s.endpoint().isPlaying()) {
            return; // the caller really was speaking, or something else is talking to them
        }
        metrics.falseBargeIn();
        log.info("[{}] no speech followed the barge-in after {} ms — treating it as noise",
                s.channelId(), props.falseInterruptionTimeoutMs());
        // The model was never actually cut off, so the next turn must not be told it was.
        s.setInterrupted(false);
        s.setCancelled(false);
        onFalseInterruption.run();
    }

    /**
     * Speak the tail of a reply the barge-in stopped, once it has turned out that nobody
     * was interrupting.
     *
     * <p>Resuming rather than re-asking is what a person does: the sentence was half out,
     * the noise was not an answer, and starting over ("uzr, takrorlang") makes the caller
     * repeat something they never said. It is also what the mature voice stacks default
     * to. Only the unspoken remainder is played — the caller already heard the rest.
     */
    private void resumeInterruptedReply(DialogSession s, int turn) {
        if (!s.busy().compareAndSet(false, true)) {
            return; // a real turn claimed the call in the meantime; it will speak
        }
        try {
            MDC.put("channelId", s.channelId());
            String rest = s.takeUnspokenText();
            if (rest == null || s.isEnded() || s.turnCount() != turn) {
                return;
            }
            if (!speech.speakChunk(s, rest)) {
                return;
            }
            String merged = mergeResumedReply(s, rest);
            s.setLastAgentText(merged);
            transcript.recordAgentLine(s, rest);
            log.info("[{}] AGENT ({}, resumed after a false barge-in): {}",
                    s.channelId(), s.state(), rest);
        } finally {
            s.busy().set(false);
            MDC.remove("channelId");
        }
    }

    /**
     * Fold a resumed tail back into the reply it belongs to, so the history holds the one
     * line the model wrote rather than two consecutive assistant messages describing half
     * a sentence each.
     *
     * @return the whole line as the caller ended up hearing it
     */
    private static String mergeResumedReply(DialogSession s, String rest) {
        List<Message> history = s.history();
        int last = history.size() - 1;
        if (last >= 0 && history.get(last) instanceof AssistantMessage spoken
                && spoken.getText() != null && !spoken.getText().isBlank()) {
            String merged = spoken.getText().trim() + " " + rest;
            history.set(last, new AssistantMessage(merged));
            return merged;
        }
        history.add(new AssistantMessage(rest));
        return rest;
    }

    // ------------------------------------------------------------------
    // The LLM call itself
    // ------------------------------------------------------------------

    /**
     * One turn, streamed: sentences are synthesized and queued as the model produces
     * them, so the caller hears the opening words while the rest is still generating.
     * This is where the turnaround budget is won — the alternative serializes the full
     * LLM latency and the full synthesis latency before any audio exists (§7.2, §1.3).
     *
     * @param speculated a reply already in flight for these exact words, or {@code null}
     *                   to send the request now. Adopting one changes nothing below it:
     *                   the stream replays what it generated during the endpointing wait
     *                   and then continues live, so sentences are spoken, tools collected
     *                   and tokens counted exactly as on any other turn
     * @return the reply text plus whether any of it actually reached the caller
     */
    private TurnResult streamTurn(DialogSession s, String system, List<Message> messages,
                                  List<ToolCallback> tools, Flux<ChatResponse> speculated) {
        TokenUsage turnUsage = new TokenUsage();
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();

        StreamedReply reply = consume(s, speculated != null ? speculated : chatClient.prompt()
                .system(system)
                .messages(messages)
                .options(turnTools.buildOptions(s, tools))
                .stream()
                .chatResponse(), turnUsage, toolCalls);
        publishUsage(s, turnUsage);
        String toolNote = turnTools.run(s, tools, toolCalls);

        if (!reply.text().isEmpty()) {
            return new TurnResult(reply.text(), reply.spoken(), reply.blocked(), toolNote);
        }
        // Tool-only turn: the model called e.g. transitionTo and produced no text of its
        // own, which is how this provider answers a tool-calling turn. The line it wrote
        // into the tool's `reply` argument is the answer — speak that (DialogTools).
        String carried = s.toolReplies();
        if (carried != null) {
            SpeechOutcome outcome = speech.speak(s, carried);
            return new TurnResult(carried, outcome == SpeechOutcome.SPOKEN,
                    outcome == SpeechOutcome.BLOCKED, toolNote);
        }
        // Nothing spoken and nothing carried — ask once more for the line alone. A whole
        // extra round trip inside the turnaround budget (§1.3), so it is the last resort.
        metrics.spokenLineRetry();
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
            toolCalls.addAll(turnTools.extractCalls(response));
            String chunk = textOf(response);
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            full.append(chunk);
            pending.append(chunk);
            if (s.isCancelled()) {
                // Still accumulating, just no longer speaking: what is left in `pending`
                // when the stream ends is exactly the part the caller did not hear, and
                // that is what a false interruption resumes from.
                continue;
            }
            for (String sentence = takeSentence(pending); sentence != null; sentence = takeSentence(pending)) {
                SpeechOutcome outcome = speech.speak(s, sentence);
                spoken |= outcome == SpeechOutcome.SPOKEN;
                blocked |= outcome == SpeechOutcome.BLOCKED;
            }
        }
        if (s.isCancelled()) {
            s.setUnspokenText(pending.toString());
        } else if (!pending.isEmpty()) {
            SpeechOutcome outcome = speech.speak(s, pending.toString().trim());
            spoken |= outcome == SpeechOutcome.SPOKEN;
            blocked |= outcome == SpeechOutcome.BLOCKED;
        }
        return new StreamedReply(full.toString().trim(), spoken, blocked);
    }

    /** One turn in a single blocking call — the pre-streaming path, kept as a fallback. */
    private TurnResult blockingTurn(DialogSession s, String system, List<Message> messages,
                                    List<ToolCallback> tools) {
        ChatResponse response = chatClient.prompt()
                .system(system)
                .messages(messages)
                .options(turnTools.buildOptions(s, tools))
                .call()
                .chatResponse();
        TokenUsage turnUsage = new TokenUsage();
        turnUsage.add(response);
        publishUsage(s, turnUsage);
        String toolNote = turnTools.run(s, tools, turnTools.extractCalls(response));

        String reply = textOf(response);
        if (reply == null || reply.isBlank()) {
            reply = s.toolReplies(); // the line the tools carried (see DialogTools)
        }
        if (reply == null || reply.isBlank()) {
            metrics.spokenLineRetry();
            log.warn("[{}] empty LLM reply in {} — asking again for the spoken line",
                    s.channelId(), s.state());
            reply = retryForSpokenLine(s, system, messages, toolNote);
            toolNote = null; // the retry has already shown it to the model
        }
        SpeechOutcome outcome = speech.speak(s, reply);
        if (outcome == SpeechOutcome.SKIPPED && s.isCancelled()) {
            // Blocking mode synthesizes the reply whole, so a barge-in costs all of it —
            // which also makes all of it what a false interruption has to resume.
            s.setUnspokenText(reply);
        }
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
        if (second != null && !second.isBlank() && speech.speak(s, second) == SpeechOutcome.SPOKEN) {
            s.history().add(new AssistantMessage(second));
            s.setLastAgentText(second);
            transcript.recordAgentLine(s, second);
            log.info("[{}] AGENT ({}, fact-guard retry): {}", s.channelId(), s.state(), second);
            return;
        }
        log.error("[{}] fact guard blocked the reply twice in {} — escalating to an operator",
                s.channelId(), s.state());
        s.end(Disposition.TRANSFERRED);
        speech.speakChunk(s, DialogPhrases.transferring(s.language()));
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
     */
    static String takeSentence(StringBuilder pending) {
        for (int i = 0; i < pending.length(); i++) {
            char c = pending.charAt(i);
            // Ignore decimal/number formatting dots and commas: e.g. 1.500.000 or 1,500
            if ((c == '.' || c == ',') && i > 0 && Character.isDigit(pending.charAt(i - 1))
                    && i + 1 < pending.length() && Character.isDigit(pending.charAt(i + 1))) {
                continue;
            }
            if (c == '.' || c == '!' || c == '?' || c == '\n' || c == '…') {
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
            // Long comma clause split (e.g. >= 30 chars) for early TTS streaming
            if (c == ',' && i >= 30) {
                String sentence = pending.substring(0, i + 1).trim();
                pending.delete(0, i + 1);
                return sentence.isEmpty() ? null : sentence;
            }
        }
        return null;
    }

    /** What one drained stream produced — the same three facts a {@link TurnResult} carries. */
    private record StreamedReply(String text, boolean spoken, boolean blocked) {
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
}
