package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.domain.enums.SpeakingWarmth;

/**
 * What the caller's mood asks of the next reply.
 *
 * <p>The mood itself is not detected here. It used to be, from regexes — and "to'lamayman"
 * read as anger and "kim bu?" as confusion whatever the tone of the call. The model now
 * judges it from the conversation and reports it with its noteCallerMood tool
 * ({@link DialogTools}); this only turns that judgement into the directive for the next turn.
 */
@Component
public class SentimentDetector {

    /**
     * The directive for the caller's mood, sized to the agent's {@link SpeakingWarmth}: a
     * RESERVED agent keeps the one-word acknowledgement, the others may answer an upset
     * caller with a sentence and a friendly one in kind.
     */
    public String empathyDirective(CustomerSentiment sentiment, String language, SpeakingWarmth warmth) {
        boolean russian = "ru-RU".equalsIgnoreCase(language);
        boolean reserved = warmth == SpeakingWarmth.RESERVED;
        if (sentiment == CustomerSentiment.FRUSTRATED) {
            return "\n\n[EMPATHY & DE-ESCALATION DIRECTIVE]: The client is upset. "
                    + (reserved
                            ? "Acknowledge it with one short word (" + (russian ? "'Понимаю.'" : "'Tushunaman.'")
                                    + ") and calmly get back to the task. "
                            : "Acknowledge it with one short, sincere sentence, speak calmly and a little slower, do not argue. ")
                    + "Do not push and do not drag out the sympathy: performed empathy is not sincere and "
                    + "irritates the client more than silence does.";
        } else if (sentiment == CustomerSentiment.CONFUSED) {
            return "\n\n[CLARITY DIRECTIVE]: The client has not understood the situation. Explain very briefly "
                    + "and simply who you are and why you are calling.";
        } else if (sentiment == CustomerSentiment.POSITIVE && !reserved) {
            // People match the warmth they are spoken to with; answering a friendly caller
            // in the same flat register is what gives the bot away.
            return "\n\n[TONE DIRECTIVE]: The client is speaking warmly — be slightly warmer too, but stay on task.";
        }
        return "";
    }
}
