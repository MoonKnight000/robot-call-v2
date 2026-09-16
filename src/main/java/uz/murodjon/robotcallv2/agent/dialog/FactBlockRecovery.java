package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.ArrayList;
import java.util.List;

/**
 * Recovery from a cascade reply the fact guard refused in full (§4.4): one corrected
 * attempt, then a hand-over to a person.
 */
@Component
class FactBlockRecovery {

    private static final Logger log = LoggerFactory.getLogger(FactBlockRecovery.class);

    private final LlmTurnClient llmTurnClient;
    private final SpeechOutput speech;
    private final DialogTranscript transcript;

    FactBlockRecovery(LlmTurnClient llmTurnClient, SpeechOutput speech, DialogTranscript transcript) {
        this.llmTurnClient = llmTurnClient;
        this.speech = speech;
        this.transcript = transcript;
    }

    /**
     * Recover from a reply the fact guard refused in full (§4.4).
     *
     * <p>Naming the mistake and asking again is usually enough — the model had the right
     * figure in its prompt all along. If the second attempt is wrong too, the call goes
     * to a person: a caller may be left waiting, but never told a sum that is not theirs.
     */
    void recover(DialogSession s, String system, List<Message> messages) {
        List<Message> retryMessages = new ArrayList<>(messages);
        retryMessages.add(new UserMessage("[TIZIM: Oxirgi javobingizda kontekstda berilmagan pul "
                + "summasi bor edi, shuning uchun u mijozga AYTILMADI. Javobni qaytadan yozing va "
                + "faqat yuqorida berilgan qarz summasini ishlating. Tool chaqirmang.]"));
        String second = null;
        try {
            ChatResponse response = llmTurnClient.callWithoutOptions(system, retryMessages);
            TokenUsage usage = new TokenUsage();
            usage.add(response);
            llmTurnClient.publishUsage(s, usage);
            second = LlmTurnClient.extractText(response);
        } catch (Exception e) {
            log.warn("[{}] fact-guard retry failed: {}", s.channelId(), e.getMessage());
        }
        if (second != null && !second.isBlank() && speech.speak(s, second) == SpeechOutcome.SPOKEN) {
            s.history().add(new AssistantMessage(second));
            s.setLastAgentText(second);
            transcript.recordAgentLine(s, second);
            log.debug("[{}] AGENT ({}, fact-guard retry): {}", s.channelId(), s.state(), second);
            return;
        }
        log.error("[{}] fact guard blocked the reply twice in {} — escalating to an operator",
                s.channelId(), s.state());
        s.end(Disposition.TRANSFERRED);
        boolean spoken = speech.speakChunk(s, DialogPhrases.transferring(s.language()));
        if (!spoken) {
            speech.speakChunk(s, DialogLines.fallback(s));
        }
    }
}
