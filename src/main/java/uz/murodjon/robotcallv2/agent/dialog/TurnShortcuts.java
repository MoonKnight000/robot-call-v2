package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.DialogMetrics;
import uz.murodjon.robotcallv2.knowledgebase.application.port.input.KnowledgeRetrievalUseCase;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeItemMatch;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * The turns answered without asking the model: the fast path for utterances whose
 * meaning is not in doubt, and fixed knowledge-base answers to policy questions.
 */
@Component
class TurnShortcuts {

    private static final Logger log = LoggerFactory.getLogger(TurnShortcuts.class);

    private final DialogProperties dialogProperties;
    private final FastPathRouter fastPathRouter;
    private final KnowledgeRetrievalUseCase knowledgeRetrieval;
    private final SpeechOutput speech;
    private final DialogTranscript transcript;
    private final DialogMetrics metrics;

    TurnShortcuts(DialogProperties dialogProperties, FastPathRouter fastPathRouter,
                  KnowledgeRetrievalUseCase knowledgeRetrieval, SpeechOutput speech, DialogTranscript transcript,
                  DialogMetrics metrics) {
        this.dialogProperties = dialogProperties;
        this.fastPathRouter = fastPathRouter;
        this.knowledgeRetrieval = knowledgeRetrieval;
        this.speech = speech;
        this.transcript = transcript;
        this.metrics = metrics;
    }

    /**
     * Settle a turn whose meaning is not in doubt, without asking the model.
     *
     * <p>"Adashibsiz", "boshqa telefon qilmang", "operatorga ulang" — three things a caller
     * says that end the call the same way every time, and that the model currently answers
     * with a full turnaround and a prompt's worth of tokens before calling the tool that
     * was always going to be called ({@link FastPathRouter}).
     *
     * <p>Everything the equivalent tool does is done here, in the same order: the stage or
     * the disposition first, then the line the caller hears, then the transcript and the
     * history. A do-not-call keeps the caller's own words as its reason, which is what the
     * tool records too. Ending is left to the same {@code finishWhenSpoken} every other
     * ending goes through, so the goodbye is heard before the channel drops.
     *
     * <p><b>The risk is a wrong match.</b> The router matches on substrings, so a sentence
     * that merely contains one of its phrases ends a call that was going fine —
     * {@code fast-path: false} turns the whole thing off without a deploy, and every match
     * is logged with the words that caused it.
     *
     * @return whether the turn was settled here and the LLM should be skipped
     */
    boolean answerFromFastPath(DialogSession s, String clientText, boolean fromClient) {
        if (!dialogProperties.fastPath() || !fromClient) {
            return false;
        }
        FastPathResult fastPath = fastPathRouter.evaluate(s, clientText);
        if (!fastPath.handled() || fastPath.reply() == null) {
            return false;
        }
        if (fastPath.nextStage() != null) {
            s.setState(fastPath.nextStage());
        }
        if (fastPath.disposition() == Disposition.DO_NOT_CALL) {
            s.setDoNotCallReason(clientText);
        }
        if (fastPath.endCall()) {
            s.end(fastPath.disposition());
        }
        s.history().add(new AssistantMessage(fastPath.reply()));
        s.setLastAgentText(fastPath.reply());
        speech.speak(s, fastPath.reply());
        transcript.recordAgentLine(s, fastPath.reply());
        metrics.fastPathHandled();
        log.debug("[{}] AGENT ({}, fast path -> {}): {}", s.channelId(), s.state(),
                fastPath.disposition(), fastPath.reply());
        if (s.isEnded()) {
            speech.finishWhenSpoken(s);
        }
        return true;
    }

    /**
     * The company's curated question-and-answer item that matches what the caller said, or null.
     * Only looked up when knowledge is on for the turn; the answer is spoken by
     * {@link #speakKnowledgeItemAnswer} without asking the model.
     *
     * <p>A curated answer never varies — it is the company's own wording, not conversation.
     * Sending it to the LLM buys a paraphrase for a full turnaround and a prompt's worth of
     * tokens, and the paraphrase is the part most likely to be wrong.
     */
    KnowledgeItemMatch findKnowledgeItemAnswer(DialogSession s, String clientText, boolean fromClient, boolean knowledgeEnabled) {
        if (!dialogProperties.knowledgeBase() || !fromClient || !knowledgeEnabled) {
            return null;
        }
        return knowledgeRetrieval.findCuratedAnswer(s.companyId(), findAgentId(s), clientText, s.language());
    }

    /**
     * Speaks a curated answer found by {@link #findKnowledgeItemAnswer}, as a proper turn: the
     * line goes through {@link SpeechOutput#speak} (so the fact guard and the spoken-audio
     * accounting still apply), into the transcript, and into the history — the model has to know
     * what the caller was told, or it will answer the follow-up as if the exchange never happened.
     *
     * @return whether the turn was answered here and the LLM should be skipped
     */
    boolean speakKnowledgeItemAnswer(DialogSession s, String answer) {
        if (answer == null) {
            return false;
        }
        s.history().add(new AssistantMessage(answer));
        s.setLastAgentText(answer);
        speech.speak(s, answer);
        transcript.recordAgentLine(s, answer);
        metrics.knowledgeBaseAnswer();
        log.debug("[{}] AGENT ({}, from the knowledge base): {}", s.channelId(), s.state(), answer);
        return true;
    }

    /** Null on a call with no agent behind it — a manual test line — which reads as "do not narrow". */
    static Long findAgentId(DialogSession s) {
        return s.agent() != null ? s.agent().id() : null;
    }
}
