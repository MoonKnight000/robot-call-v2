package uz.murodjon.uysotvoice.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Real-time conversational sentiment analyzer that detects customer frustration,
 * agitation, confusion, or satisfaction from their spoken utterances.
 */
@Component
public class SentimentDetector {

    private static final Logger log = LoggerFactory.getLogger(SentimentDetector.class);

    private static final Pattern ANGER_FRUSTRATION_UZ = Pattern.compile(
            "\\b(jonga tegdingiz|asabimga tegmang|yetar|eshitishni xohlamayman|yo'qol|lanat|axmoq|tushunmayapsiz|bormayman|to'lamayman|qayerdan oldingiz|adashdingiz|bezdirdingiz|o'chir telefonni|bezovta qilmang)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern ANGER_FRUSTRATION_RU = Pattern.compile(
            "\\b(достали|задолбали|хватит|отстаньте|не звоните|вы издеваетесь|не понимаю|пошел|мошенники|надоели|не хочу слушать|заблокирую)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern CONFUSION = Pattern.compile(
            "\\b(tushunmadim|qanaqa qarz|kim bu|qayerdan|nima deyapsiz|qaysi kompaniya|ne ponimayu|kto eto|kakoy dolg|o chem vy)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public enum CustomerSentiment {
        POSITIVE,
        NEUTRAL,
        CONFUSED,
        FRUSTRATED
    }

    /**
     * Evaluates customer text for sentiment triggers.
     */
    public CustomerSentiment analyze(String text) {
        if (text == null || text.isBlank()) {
            return CustomerSentiment.NEUTRAL;
        }
        if (ANGER_FRUSTRATION_UZ.matcher(text).find() || ANGER_FRUSTRATION_RU.matcher(text).find()) {
            log.info("Detected FRUSTRATED customer sentiment: {}", text);
            return CustomerSentiment.FRUSTRATED;
        }
        if (CONFUSION.matcher(text).find()) {
            return CustomerSentiment.CONFUSED;
        }
        return CustomerSentiment.NEUTRAL;
    }

    /**
     * Dynamic empathy prompt addition if customer is agitated.
     */
    public String empathyDirective(CustomerSentiment sentiment, String language) {
        if (sentiment == CustomerSentiment.FRUSTRATED) {
            if ("ru-RU".equalsIgnoreCase(language)) {
                return "\n\n[EMPATHY & DE-ESCALATION DIRECTIVE]: Клиент раздражен или взволнован! "
                        + "Проявите максимальное сочувствие и вежливость. Начните ответ с теплой эмпатии "
                        + "('Я вас прекрасно понимаю, давайте спокойно разберемся...'). "
                        + "Говорите мягче, не повторяйте роботизированных фраз и не давите.";
            } else {
                return "\n\n[HAMDARDLIK VA TINCHLANTIRISH QOIDASI]: Mijoz asabiylashgan yoki norozi! "
                        + "Maksimal darajada xushmuomala va hamdard bo'ling. Javobni vaziyatni tushunishingizni "
                        + "bildiruvchi iliq gap bilan boshlang ('Sizni juda yaxshi tushunib turibman, keling, birgalikda xotirjam ko'rib chiqamiz...'). "
                        + "Bosim qilmang, robotdek bir xil gapni takrorlamang.";
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
