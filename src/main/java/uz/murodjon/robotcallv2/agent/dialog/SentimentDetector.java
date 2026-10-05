package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;

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
     * Dynamic empathy prompt addition if customer is agitated.
     */
    public String empathyDirective(CustomerSentiment sentiment, String language) {
        if (sentiment == CustomerSentiment.FRUSTRATED) {
            if ("ru-RU".equalsIgnoreCase(language)) {
                return "\n\n[EMPATHY & DE-ESCALATION DIRECTIVE]: Клиент раздражен. "
                        + "Признайте это одним коротким словом ('Понимаю.') и спокойно вернитесь к делу. "
                        + "Не давите и не растягивайте сочувствие: разыгранная эмпатия звучит фальшиво "
                        + "и раздражает сильнее молчания.";
            } else {
                return "\n\n[HAMDARDLIK VA TINCHLANTIRISH QOIDASI]: Mijoz asabiylashgan. "
                        + "Buni bitta qisqa so'z bilan tan oling ('Tushunaman.') va xotirjam ohangda ishga qayting. "
                        + "Bosim qilmang, hamdardlikni cho'zmang: o'ynalgan hamdardlik samimiy emas va "
                        + "mijozni jimlikdan ko'ra ko'proq asabiylashtiradi.";
            }
        } else if (sentiment == CustomerSentiment.CONFUSED) {
            if ("ru-RU".equalsIgnoreCase(language)) {
                return "\n\n[CLARITY DIRECTIVE]: Клиент не понял суть. Кратко и доступно поясните, кто вы и в чем вопрос.";
            } else {
                return "\n\n[TUSHUNTIRISH QOIDASI]: Mijoz vaziyatni to'liq tushunmadi. Kimligingiz va murojaat maqsadingizni juda qisqa va sodda qilib tushuntiring.";
            }
        }
        return "";
    }
}
