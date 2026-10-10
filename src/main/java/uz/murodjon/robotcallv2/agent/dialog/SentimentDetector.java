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
            if (russian) {
                return "\n\n[EMPATHY & DE-ESCALATION DIRECTIVE]: Клиент раздражен. "
                        + (reserved
                                ? "Признайте это одним коротким словом ('Понимаю.') и спокойно вернитесь к делу. "
                                : "Признайте это одной короткой искренней фразой, говорите спокойно и чуть медленнее, не спорьте. ")
                        + "Не давите и не растягивайте сочувствие: разыгранная эмпатия звучит фальшиво "
                        + "и раздражает сильнее молчания.";
            }
            return "\n\n[HAMDARDLIK VA TINCHLANTIRISH QOIDASI]: Mijoz asabiylashgan. "
                    + (reserved
                            ? "Buni bitta qisqa so'z bilan tan oling ('Tushunaman.') va xotirjam ohangda ishga qayting. "
                            : "Buni bitta qisqa, samimiy gap bilan tan oling, xotirjam va sekinroq gapiring, tortishmang. ")
                    + "Bosim qilmang, hamdardlikni cho'zmang: o'ynalgan hamdardlik samimiy emas va "
                    + "mijozni jimlikdan ko'ra ko'proq asabiylashtiradi.";
        } else if (sentiment == CustomerSentiment.CONFUSED) {
            if (russian) {
                return "\n\n[CLARITY DIRECTIVE]: Клиент не понял суть. Кратко и доступно поясните, кто вы и в чем вопрос.";
            }
            return "\n\n[TUSHUNTIRISH QOIDASI]: Mijoz vaziyatni to'liq tushunmadi. Kimligingiz va murojaat maqsadingizni juda qisqa va sodda qilib tushuntiring.";
        } else if (sentiment == CustomerSentiment.POSITIVE && !reserved) {
            // People match the warmth they are spoken to with; answering a friendly caller
            // in the same flat register is what gives the bot away.
            if (russian) {
                return "\n\n[TONE DIRECTIVE]: Клиент говорит дружелюбно — ответьте чуть теплее, но не отвлекайтесь от дела.";
            }
            return "\n\n[OHANG QOIDASI]: Mijoz iliq gapiryapti — siz ham sal iliqroq bo'ling, lekin ishdan chalg'imang.";
        }
        return "";
    }
}
