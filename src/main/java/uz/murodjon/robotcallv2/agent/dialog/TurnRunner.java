package uz.murodjon.robotcallv2.agent.dialog;

import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import uz.murodjon.robotcallv2.agent.metrics.LlmMetrics;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgePassage;
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
 * <p>The model call itself is {@link LlmTurnClient}'s, and so is whether dialog is
 * possible at all: without an LLM ChatModel bean (no {@code GEMINI_API_KEY} or
 * {@code OPENAI_API_KEY}) the app still starts and {@link #available()} is false. Guessing
 * the reply early is {@link SpeculationManager}'s, recovering from a barge-in
 * {@link BargeInRecovery}'s, and the turns answered without the model {@link TurnShortcuts}'.
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

    private final DialogProperties dialogProperties;
    private final SystemPromptFactory promptFactory;
    private final LlmMetrics metrics;
    private final TurnTools turnTools;
    private final SpeechOutput speech;
    private final DialogTranscript transcript;
    private final DialogExecutors executors;
    private final KnowledgeRetrievalUseCase knowledgeRetrieval;
    private final DialogTuningProperties dialogTuning;
    private final DialogPolicy dialogPolicy;
    private final LlmTurnClient llmTurnClient;
    private final SpeculationManager speculationManager;
    private final BargeInRecovery bargeInRecovery;
    private final FactBlockRecovery factBlockRecovery;
    private final TurnShortcuts turnShortcuts;

    public TurnRunner(DialogProperties dialogProperties,
                      SystemPromptFactory promptFactory,
                      LlmMetrics metrics,
                      TurnTools turnTools,
                      SpeechOutput speech,
                      DialogTranscript transcript,
                      DialogExecutors executors,
                      KnowledgeRetrievalUseCase knowledgeRetrieval,
                      DialogTuningProperties dialogTuning,
                      DialogPolicy dialogPolicy,
                      LlmTurnClient llmTurnClient,
                      SpeculationManager speculationManager,
                      BargeInRecovery bargeInRecovery,
                      FactBlockRecovery factBlockRecovery,
                      TurnShortcuts turnShortcuts) {
        this.dialogProperties = dialogProperties;
        this.promptFactory = promptFactory;
        this.metrics = metrics;
        this.turnTools = turnTools;
        this.speech = speech;
        this.transcript = transcript;
        this.executors = executors;
        this.knowledgeRetrieval = knowledgeRetrieval;
        this.dialogTuning = dialogTuning;
        this.dialogPolicy = dialogPolicy;
        this.llmTurnClient = llmTurnClient;
        this.speculationManager = speculationManager;
        this.bargeInRecovery = bargeInRecovery;
        this.factBlockRecovery = factBlockRecovery;
        this.turnShortcuts = turnShortcuts;
    }

    /** Whether a turn could run at all right now. */
    public boolean available() {
        return dialogProperties.enabled() && llmTurnClient.isReady();
    }

    /** The opening turn: nobody has spoken yet, so the bootstrap line stands in for the caller. */
    public void startGreeting(DialogSession s) {
        if (s.firstMessage() != null && !s.firstMessage().isBlank()) {
            String opening = s.firstMessage().trim();
            log.debug("[{}] AGENT (firstMessage): {}", s.channelId(), opening);
            speech.speakChunk(s, opening);
            s.history().add(new AssistantMessage(opening));
            s.setLastAgentText(opening);
            transcript.recordAgentLine(s, opening);
            return;
        }
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
        // Read before the busy claim: a barge-in stamped after this moment belongs to this
        // turn and must survive the clear below.
        long turnStartedAt = System.nanoTime();
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
            }
            // Cleared before the caps below, not after they have been checked: those close
            // the call with a spoken farewell, and a previous turn's barge-in flag left
            // standing would swallow it — the caller's last experience of the call would
            // be the line going dead. Only a flag raised before this turn began is cleared,
            // atomically: clearing it outright wiped a barge-in that landed just after the
            // busy claim, and the new reply then played over a caller who was talking.
            s.clearCancelledBefore(turnStartedAt);
            int maxCallSeconds = dialogPolicy.resolveMaxCallSeconds(s.aiModel().maxCallSeconds(), s.agent());
            if (maxCallSeconds > 0 && Duration.between(s.startedAt(), Instant.now()).getSeconds() > maxCallSeconds) {
                closeOnLimit(s, "maksimal davomiylik");
                return;
            }
            // Counted on every turn, cap or no cap: the count also drives speculation and
            // the outcome bookkeeping. A max-turns of 0 means no cap, as on the realtime pipeline.
            if (DialogPolicy.isTurnCapExceeded(s.incrementTurn(), dialogProperties.maxTurns())) {
                closeOnLimit(s, "maksimal turn soni");
                return;
            }
            // Cost cap (§C14). The turn and duration caps bound a call that behaves;
            // this one bounds a call that does not — a model stuck in a loop resends the
            // whole context every turn, and the bill grows even while the turn count
            // looks reasonable.
            if (DialogPolicy.isTokenBudgetSpent(s.aiModel().maxTokensPerCall(), s.tokensUsed())) {
                closeOnLimit(s, "token budjeti (" + s.tokensUsed() + " token)");
                return;
            }
            // The outcome is recorded and the model has had a turn to say goodbye with it
            // (the annex tells it to) and did not. Everything worth agreeing has been
            // agreed, so the call is over whatever the model thinks: on a real call it
            // recorded the promise, confirmed it out loud, and then simply kept the line
            // open until the caller hung up. end(null) keeps the outcome that was recorded.
            if (s.turnsSinceOutcome() >= 2) {
                closeOnLimit(s, "outcome recorded and the model did not close the call");
                return;
            }

            int stageAttempts = s.recordStageAttempt(s.state());
            if (stageAttempts >= dialogTuning.maxStageAttempts()) {
                log.warn("[{}] FSM infinite loop detected: {} turns stuck in state '{}' - escalating to operator",
                        s.channelId(), stageAttempts, s.state());
                s.end(Disposition.TRANSFERRED);
                speech.speakChunk(s, DialogPhrases.transferring(s.language()));
                return;
            }

            if (fromClient) {
                clientText = bargeInRecovery.stitchUnansweredUtterance(s, clientText);
            }
            s.history().add(new UserMessage(clientText));
            int dropped = s.trimHistory(dialogProperties.historyMaxMessages(), HISTORY_TRIM_BLOCK);
            if (dropped > 0) {
                log.debug("[{}] history trimmed by {} messages (cap {})",
                        s.channelId(), dropped, dialogProperties.historyMaxMessages());
            }

            String system = llmTurnClient.resolveSystemPrefix(s);
            // Looked up before the annex is built, because that is where the passages go.
            retrieveKnowledge(s, clientText, fromClient);
            // The state block goes after the history, not into the system message, so
            // the cached prefix stays append-only (see SystemPromptFactory).
            List<Message> messages = new ArrayList<>(s.history());
            messages.add(new UserMessage(promptFactory.turnAnnex(s)));
            s.setInterrupted(false); // the annex has carried the barge-in note now

            List<ToolCallback> tools = turnTools.build(s);
            s.clearToolReplies(); // the last turn's lines have been spoken or dropped
            s.clearWaitForCaller();
            s.clearSpokenText();  // this turn's own account of what the caller hears
            s.setUnspokenText(null); // a previous turn's cut-off tail is stale now

            if (turnShortcuts.answerFromFastPath(s, clientText, fromClient)
                    || turnShortcuts.answerFromKnowledgeBase(s, clientText, fromClient)) {
                return;
            }

            // A reply that was already being written while the caller finished speaking,
            // if the final turned out to say what the interim did. Taken before the timer
            // starts on purpose: the LLM latency this turn is charged with should be the
            // part the caller actually waited through.
            Flux<ChatResponse> speculated = speculationManager.adoptSpeculation(s, clientText, fromClient);

            TurnResult result;
            String model = llmTurnClient.selectModel(s, clientText, fromClient);
            s.latency().llmRequested();
            Timer.Sample llmSample = metrics.startTimer();
            ScheduledFuture<?> filler = speech.scheduleFiller(s);
            s.endpoint().startThinking();
            try {
                result = llmTurnClient.runTurn(s, system, messages, tools, speculated, model);
            } catch (Exception e) {
                metrics.llmError();
                log.warn("LLM turn failed [{}]: {}", s.channelId(), e.getMessage());
                // Fall through with no reply rather than returning: the fallback below
                // still has to speak. Returning here left the caller listening to
                // silence, and in GREETING that is the whole call — the bot never
                // says anything at all.
                result = llmTurnClient.retryTurn(s, system, messages, tools, model, e);
            } finally {
                if (filler != null) {
                    // The turn is over; anything still pending would land after the reply.
                    // A filler already in flight is stopped by its own guards instead.
                    filler.cancel(false);
                }
                s.endpoint().stopThinking();
                long elapsedNanos = metrics.stopLlmTurn(llmSample);
                s.recordLlmLatency(Duration.ofNanos(elapsedNanos).toMillis());
            }

            // A barge-in landed: the history, the transcript and the "where you stopped"
            // note record what the caller HEARD, which is no longer what the model wrote.
            // Told it delivered the whole reply, the model treats the sentences the caller
            // never heard as said, and the sum and the due date never come up again.
            boolean cutOff = s.isCancelled();
            String reply = cutOff ? s.spokenText() : result.reply();
            if (SystemPromptFactory.isSystemNote(reply) || SpeechSanitizer.isUnspeakable(reply)) {
                // Not spoken (SpeechOutput refused it) and not remembered either: an
                // assistant turn made of the annex teaches the model that reciting it is
                // an answer, and the next turn recites it again.
                log.warn("[{}] model produced unspeakable/code token '{}' in {} — dropped",
                        s.channelId(), reply, s.state());
                reply = null;
            }
            boolean haveText = reply != null && !reply.isBlank();
            if (haveText) {
                s.history().add(new AssistantMessage(reply));
                s.setLastAgentText(reply); // remembered so barge-in can tell the model where it stopped
                log.debug("[{}] AGENT ({}{}): {}", s.channelId(), s.state(),
                        cutOff ? ", cut off by the caller" : "", reply);
                transcript.recordAgentLine(s, reply);
            } else if (s.isWaitingForCaller() && fromClient) {
                // The model heard an unfinished thought and chose to keep listening. The
                // words are kept for the next final to be stitched onto, so the model sees
                // the whole sentence then rather than its two halves as separate turns.
                log.info("[{}] model is waiting for the caller to finish", s.channelId());
                s.setUnansweredClientText(clientText);
            } else if (cutOff && fromClient) {
                // Silenced before the first word: the caller heard nothing, so as far as they
                // know their sentence is still being listened to — and the next final is
                // probably the rest of it. Kept for that turn to fold in; the false-interruption
                // check clears it if it turns out nobody was speaking and the reply resumes.
                s.setUnansweredClientText(clientText);
            }
            if (result.toolNote() != null && (result.toolNote().contains("XATO")
                    || result.toolNote().contains(ToolConfirmation.CONFIRMATION_REQUIRED))) {
                // The model spoke and called a tool in the same breath, and the tool
                // refused it (§4.4 — a promise dated in the past, say). There was no
                // retry to show it the rejection, so the next turn carries it instead;
                // otherwise the model believes a promise that was never recorded. A write
                // held for the caller's yes is the same: nothing was sent, and the model
                // has to know that, and what to read back.
                s.history().add(new UserMessage("[TIZIM: Tool natijasi: " + result.toolNote() + "]"));
            }

            if (cutOff) {
                // Nothing more is spoken into a caller who is talking. Neither recovery
                // below applies: both exist to break a silence, and there is none.
                log.debug("[{}] turn cut off by the caller — no recovery line", s.channelId());
            } else if (result.blocked() && !result.spoken()) {
                // The whole reply was a figure the caller must not be told. Correct it
                // once, then hand over to a person — never guess a sum aloud (§4.4).
                factBlockRecovery.recover(s, system, messages);
            } else if (!result.spoken() && !s.isEnded() && !s.isWaitingForCaller()) {
                // Nothing reached the caller and the call is not over: speak a neutral
                // line rather than hand them silence.
                if (!haveText) {
                    metrics.llmError();
                    log.warn("[{}] no speakable text from the LLM in {}; using the fallback line",
                            s.channelId(), s.state());
                }
                speech.speakChunk(s, DialogLines.fallback(s));
            }
            if (!s.isEnded() && !cutOff && haveText && Farewells.saidByAgent(reply)) {
                // The model wrote a goodbye and reached for some other tool to say it with.
                // endCall is what sets the flag the hangup hangs off, so without this the
                // farewell goes out into a line that stays open until the caller drops it —
                // which is how a 65-second call ends with the bot saying goodbye twice.
                log.info("[{}] agent said goodbye without calling endCall — closing the call",
                        s.channelId());
                s.end(null);
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
                bargeInRecovery.scheduleResume(s, turn);
            }
        }
    }

    /** See {@link SpeculationManager#speculate}. */
    public void speculate(DialogSession s, String text) {
        speculationManager.speculate(s, text);
    }

    /** See {@link BargeInRecovery#scheduleFalseInterruptionCheck}. */
    public void scheduleFalseInterruptionCheck(DialogSession s, int turn, Runnable onFalseInterruption) {
        bargeInRecovery.scheduleFalseInterruptionCheck(s, turn, onFalseInterruption);
    }

    private void closeOnLimit(DialogSession s, String reason) {
        log.info("[{}] closing dialog ({})", s.channelId(), reason);
        s.end(null);
        speech.speakChunk(s, DialogLines.farewell(s));
        speech.finishWhenSpoken(s);
    }

    /**
     * Puts the company's own documents behind this turn, for agents that asked for it.
     *
     * <p>Off unless the agent has {@code useRag}: a search costs one embedding round trip
     * inside a turnaround budget the caller is sitting through, and an agent working from
     * a scripted scenario has nothing to look up. Cleared rather than left alone when it
     * is off or finds nothing, so the previous turn's passages never answer this one's
     * question.
     *
     * <p>Never allowed to fail the turn: a knowledge base that is down is a turn answered
     * without it, not a call that stops.
     */
    private void retrieveKnowledge(DialogSession s, String clientText, boolean fromClient) {
        if (!fromClient || s.agent() == null || !s.agent().useRag()) {
            s.setKnowledgePassages(List.of());
            return;
        }
        try {
            List<KnowledgePassage> passages = knowledgeRetrieval.findRelevantPassages(
                    s.companyId(), TurnShortcuts.findAgentId(s), clientText, dialogProperties.knowledgePassages());
            s.setKnowledgePassages(passages);
            if (!passages.isEmpty()) {
                log.debug("[{}] knowledge base contributed {} passages (best {})",
                        s.channelId(), passages.size(), String.format("%.2f", passages.getFirst().score()));
            }
        } catch (Exception e) {
            s.setKnowledgePassages(List.of());
            log.warn("[{}] knowledge search failed, answering without it: {}", s.channelId(), e.getMessage());
        }
    }
}
