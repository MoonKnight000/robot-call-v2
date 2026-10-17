package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import uz.murodjon.robotcallv2.agent.metrics.LlmMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Writing the reply before the caller has finished saying it (§1.3, {@link Speculation}):
 * starts a speculative request from an interim transcript, and decides at the start of
 * the turn whether the parked guess answers what the final actually said.
 */
@Component
class SpeculationManager {

    private static final Logger log = LoggerFactory.getLogger(SpeculationManager.class);

    private final DialogProperties dialogProperties;
    private final DialogExecutors executors;
    private final LlmMetrics metrics;
    private final SpeechOutput speech;
    private final SystemPromptFactory promptFactory;
    private final TurnTools turnTools;
    private final LlmTurnClient llmTurnClient;

    SpeculationManager(DialogProperties dialogProperties, DialogExecutors executors, LlmMetrics metrics,
                       SpeechOutput speech, SystemPromptFactory promptFactory, TurnTools turnTools,
                       LlmTurnClient llmTurnClient) {
        this.dialogProperties = dialogProperties;
        this.executors = executors;
        this.metrics = metrics;
        this.speech = speech;
        this.promptFactory = promptFactory;
        this.turnTools = turnTools;
        this.llmTurnClient = llmTurnClient;
    }

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
    void speculate(DialogSession s, String text) {
        if (text == null) {
            return;
        }
        if (!dialogProperties.preemptive() || !dialogProperties.streaming()) {
            s.setSpeculationBlocker("preemptive or streaming switched off in config");
            return;
        }
        String blocker = speculationBlocker(s, text);
        if (blocker != null) {
            // Kept for the one summary line this turn gets (see adoptSpeculation) rather
            // than logged here: an utterance produces a dozen interims and every one of
            // them would say the same thing.
            s.setSpeculationBlocker(blocker);
            return;
        }
        Speculation parked = s.speculation();
        if (parked != null && TranscriptText.saysTheSame(parked.inputText(), text)) {
            return; // the same hypothesis, already being written
        }
        executors.submit(() -> startSpeculation(s, text));
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
    Flux<ChatResponse> adoptSpeculation(DialogSession s, String clientText, boolean fromClient) {
        Speculation parked = s.takeSpeculation();
        try {
            if (!fromClient || !dialogProperties.preemptive() || !dialogProperties.streaming()) {
                // Nothing was guessed at and nothing was meant to be: the greeting has no
                // caller utterance in front of it, and a switched-off feature reporting
                // itself every turn is noise, not a diagnostic.
                if (parked != null) {
                    parked.discard();
                }
                return null;
            }
            if (parked == null) {
                // Nothing was written ahead, and which of the two reasons it was decides
                // where to look: no interims at all is a recognizer or wiring problem,
                // interims that were all turned away is a condition in speculationBlocker.
                log.info("[{}] speculation: none — {} interim(s) this turn{}", s.channelId(),
                        s.interimsThisTurn(),
                        s.speculationBlocker() != null ? ", last one blocked: " + s.speculationBlocker() : "");
                return null;
            }
            if (fromClient && isSpeculationCompatible(parked.inputText(), clientText)) {
                metrics.speculationHit();
                s.latency().speculationAdopted();
                log.info("[{}] speculation: hit after {} interim(s) — the reply was already written",
                        s.channelId(), s.interimsThisTurn());
                return parked.responses();
            }
            parked.discard();
            metrics.speculationMiss();
            // Both texts, because the fix depends on how far apart they are: a final that
            // merely adds the last few words is a min-chars/timing question, one that
            // rewrites the whole sentence means this recognizer's interims are not worth
            // guessing on at all.
            log.info("[{}] speculation: miss after {} interim(s)", s.channelId(), s.interimsThisTurn());
            log.debug("[{}] speculation guessed on '{}', final said '{}'", s.channelId(), parked.inputText(), clientText);
            return null;
        } finally {
            s.resetSpeculationDiagnostics();
        }
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
    private String speculationBlocker(DialogSession s, String text) {
        if (s.isEnded()) {
            return "call ended";
        }
        if (s.busy().get()) {
            return "a turn is already running";
        }
        if (s.isInterrupted()) {
            return "the caller interrupted the bot";
        }
        if (s.turnCount() == 0) {
            return "the bot has not spoken yet";
        }
        if (text.length() < dialogProperties.preemptiveMinChars() && !isKnownShortUtterance(text)) {
            return "interim shorter than preemptive-min-chars=" + dialogProperties.preemptiveMinChars();
        }
        if (!withinSpeculationBudget(s)) {
            return "call token budget nearly spent";
        }
        return null;
    }

    private static final Set<String> COMPLETE_SHORT_UTTERANCES = Set.of(
            "ha", "yoq", "yo'q", "aha", "shunday", "mayli", "xop", "xo'p", "to'g'ri", "togri", "albatta",
            "да", "нет", "ага", "так", "ясно", "ладно", "верно", "конечно"
    );

    static boolean isKnownShortUtterance(String text) {
        if (text == null) {
            return false;
        }
        String norm = TranscriptText.normalize(text);
        return COMPLETE_SHORT_UTTERANCES.contains(norm);
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
     * <p>The prompt is built practical as {@code TurnRunner#advance} would build it, but
     * from a copy of the history: nothing here may leave a mark on the session, because the
     * caller may yet say something else entirely.
     */
    private void startSpeculation(DialogSession s, String text) {
        String blocker = speculationBlocker(s, text);
        if (blocker != null) {
            s.setSpeculationBlocker(blocker); // a turn claimed the call between the submit and here
            return;
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
            // Replayed and eagerly connected — see LlmTurnClient#openSpeculativeStream.
            AtomicReference<Disposable> upstream = new AtomicReference<>();
            Flux<ChatResponse> responses = llmTurnClient.openSpeculativeStream(s,
                    llmTurnClient.resolveSystemPrefix(s), messages,
                    turnTools.build(s), llmTurnClient.selectModel(s, text, true), upstream);
            StringBuilder guessed = new StringBuilder();
            AtomicBoolean firstSentenceWarmed = new AtomicBoolean();
            Disposable warmUp = responses.subscribe(
                    response -> warmFirstSentence(s, response, guessed, firstSentenceWarmed),
                    error -> log.debug("[{}] speculative reply failed: {}", s.channelId(), error.toString()));
            s.setSpeculation(new Speculation(text, responses, warmUp, upstream.get()));
            metrics.speculationStarted();
            log.debug("[{}] speculating on: {}", s.channelId(), text);
        } catch (Exception e) {
            // A guess that cannot be started costs the turn nothing — it runs as before.
            log.debug("[{}] could not start a speculative reply: {}", s.channelId(), e.getMessage());
        } finally {
            MDC.remove("channelId");
        }
    }

    /**
     * Send the guess's first sentence to synthesis as soon as it is complete, so the audio
     * is already made if the turn adopts it ({@code SpeechOutput#warmSentence}).
     *
     * <p>Only the first: everything after it is generated while the caller is already
     * listening to that one, which is the overlap streaming exists for. Runs on the
     * reactor's thread, so the synthesis itself is handed to a worker — blocking here
     * would stall the very stream it is trying to get ahead of.
     */
    private void warmFirstSentence(DialogSession s, ChatResponse response,
                                   StringBuilder guessed, AtomicBoolean warmed) {
        if (!dialogProperties.preemptiveTts() || warmed.get()) {
            return;
        }
        String chunk = LlmTurnClient.extractText(response);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        guessed.append(chunk);
        String sentence = SentenceSplitter.takeSentence(guessed);
        if (sentence != null && warmed.compareAndSet(false, true)) {
            executors.submit(() -> speech.warmSentence(s, sentence));
        }
    }
}
