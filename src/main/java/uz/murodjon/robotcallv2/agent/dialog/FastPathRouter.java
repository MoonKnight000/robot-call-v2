package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Locale;
import java.util.Set;

/**
 * Deterministic Fast-Path Router for ultra-low latency (<20ms) dialog turns.
 *
 * <p>Certain conversational turns (e.g. wrong number declaration, human operator request,
 * simple opt-out request) do not require an open-ended LLM inference round trip.
 * By matching high-confidence intents deterministically, this router fulfills the turn
 * immediately from cached audio/code phrases with zero LLM token cost and zero LLM delay.
 */
@Component
public class FastPathRouter {

    private static final Logger log = LoggerFactory.getLogger(FastPathRouter.class);

    private static final Set<String> WRONG_PERSON_UZ = Set.of(
            "men emasman", "men emas", "adashdingiz", "boshqa odam", "bunaqa odam yo'q",
            "noto'g'ri tushdingiz", "notogri tushdingiz", "noto'g'ri raqam", "boshqa raqam",
            "adashibsiz", "bu u emas", "bu yerda yashamaydi", "tanimayman"
    );

    private static final Set<String> WRONG_PERSON_RU = Set.of(
            "вы ошиблись", "ошиблись", "не туда попали", "здесь таких нет", "я не тот",
            "неправильный номер", "не туда звоните", "не знаю такого"
    );

    private static final Set<String> DO_NOT_CALL_UZ = Set.of(
            "boshqa telefon qilmang", "telefon qilmang", "qaytib qilmang", "raqamimni o'chiring",
            "bezovta qilmang", "spam", "bloklayman", "o'chirib tashlang", "telefon qilmanglar"
    );

    private static final Set<String> DO_NOT_CALL_RU = Set.of(
            "не звоните больше", "не звоните", "удалите мой номер", "удалите номер", "не беспокойте"
    );

    private static final Set<String> TRANSFER_UZ = Set.of(
            "operatorga ula", "operatorga ulang", "operator bilan gaplashmoqchiman",
            "operator chaqir", "odam bilan gaplashay", "tirik odam bormi", "mutaxassisga ulang", "operator"
    );

    private static final Set<String> TRANSFER_RU = Set.of(
            "соедините с оператором", "переведите на оператора", "хочу поговорить с человеком",
            "дайте оператора", "живой человек есть", "свяжите с оператором"
    );

    private static final Set<String> CLOSING_GRATITUDE_UZ = Set.of(
            "rahmat xayr", "katta rahmat xayr", "rahmat salomat boling", "rahmat salomat bo'ling",
            "katta rahmat salomat boling", "katta rahmat salomat bo'ling", "rahmat sog boling", "rahmat sog' bo'ling",
            "raxmat xayr", "tashakkur xayr", "boshqa savol yoq", "boshqa savol yo'q",
            "boshqa savolim yoq", "boshqa savolim yo'q", "hammasi tushunarli rahmat", "boldi rahmat", "bo'ldi rahmat",
            "rahmat boldi", "rahmat bo'ldi", "rahmat tushundim", "tushundim rahmat"
    );

    private static final Set<String> CLOSING_GRATITUDE_RU = Set.of(
            "спасибо до свидания", "большое спасибо до свидания", "спасибо всего доброго",
            "большое спасибо всего хорошего", "все понятно спасибо", "нет вопросов спасибо",
            "спасибо больше ничего не надо", "понял спасибо до свидания", "понятно спасибо до свидания"
    );

    private static final Set<String> SIMPLE_GRATITUDE_UZ = Set.of(
            "rahmat", "katta rahmat", "raxmat", "tashakkur", "salomat boling", "salomat bo'ling", "sog boling", "sog' bo'ling"
    );

    private static final Set<String> SIMPLE_GRATITUDE_RU = Set.of(
            "спасибо", "большое спасибо", "благодарю", "всего доброго", "до свидания"
    );

    private static final Set<String> REPEAT_UZ = Set.of(
            "nima dedingiz", "nima deding", "nima deyapsiz", "nima deysiz", "nima deyishdi",
            "eshitmay qoldim", "eshitmadim", "eshitolmadim", "qaytaring", "qaytarib yuboring",
            "qaytarvoring", "tushunmadim", "qaytadan ayting", "qaytadan aytvorasizmi",
            "qaytadan aytingchi", "a", "nima"
    );

    private static final Set<String> REPEAT_RU = Set.of(
            "повторите", "повторите пожалуйста", "что вы сказали", "что ты сказал",
            "не расслышал", "не услышал", "не понял", "плохо слышно", "что", "а"
    );

    private static final Set<String> IDENTITY_UZ = Set.of(
            "siz kimsiz", "kim bu", "kim bu o'zi", "kim bu ozi", "robotmisiz", "odammisiz",
            "sun'iy intellektmisiz", "suniy intellektmisiz", "botmisiz", "avtooperator",
            "avtojavobgich", "kim bilan gaplashyapman", "kim bilan gaplashayapman", "siz kimsiz ozi", "siz kimsiz o'zi"
    );

    private static final Set<String> IDENTITY_RU = Set.of(
            "кто это", "кто говорит", "вы робот", "вы человек", "с кем я говорю", "это бот", "вы бот"
    );

    private static final Set<String> PURPOSE_UZ = Set.of(
            "nima maqsadda telefon qilyapsiz", "nima maqsadda qo'ng'iroq qilyapsiz", "nima maqsadda",
            "nega telefon qildingiz", "nega telefon qilyapsiz", "nega qildingiz", "nima uchun telefon qilyapsiz",
            "nima masala", "tinchlikmi", "qanday masala", "nima gap"
    );

    private static final Set<String> PURPOSE_RU = Set.of(
            "с какой целью звоните", "с какой целью", "зачем звоните", "по какому поводу",
            "по какому вопросу", "в чем дело", "что случилось"
    );

    public FastPathResult evaluate(DialogSession s, String clientText) {
        if (clientText == null || clientText.isBlank() || s.isEnded()) {
            return FastPathResult.NOT_HANDLED;
        }

        String normalized = normalize(clientText);
        String[] words = normalized.split("\\s+");
        boolean isRu = s.language() != null && s.language().startsWith("ru");

        // 1. Wrong person / wrong number detection
        if (matchesAny(normalized, isRu ? WRONG_PERSON_RU : WRONG_PERSON_UZ)) {
            String reply = isRu
                    ? "Извините, мы ошиблись номером. Всего доброго."
                    : "Kechirasiz, adashibmiz. Xayr, salomat bo'ling.";
            log.info("[{}] Fast-path matched: WRONG_NUMBER", s.channelId());
            return FastPathResult.endWithDisposition(reply, Disposition.WRONG_NUMBER);
        }

        // 2. Do Not Call / Opt-out
        if (matchesAny(normalized, isRu ? DO_NOT_CALL_RU : DO_NOT_CALL_UZ)) {
            String reply = isRu
                    ? "Понятно, ваш номер исключен из базы. Извините за беспокойство."
                    : "Tushundim, raqamingiz ro'yxatdan chiqariladi. Bezovta qilganimiz uchun uzr.";
            log.info("[{}] Fast-path matched: DO_NOT_CALL", s.channelId());
            return FastPathResult.endWithDisposition(reply, Disposition.DO_NOT_CALL);
        }

        // 3. Human Transfer Request
        if (matchesAny(normalized, isRu ? TRANSFER_RU : TRANSFER_UZ)) {
            String reply = DialogPhrases.transferring(s.language());
            log.info("[{}] Fast-path matched: TRANSFER", s.channelId());
            return FastPathResult.endWithDisposition(reply, Disposition.TRANSFERRED);
        }

        // 4. Closing Gratitude / Polite Farewell
        if (isClosingGratitude(normalized, isRu, s)) {
            String reply = DialogPhrases.gratitudeReply(s.language());
            log.info("[{}] Fast-path matched: GRATITUDE_CLOSE", s.channelId());
            Disposition disp = s.disposition() != null ? s.disposition() : Disposition.ANSWERED;
            return FastPathResult.endWithDisposition(reply, disp);
        }

        // 5. Repeat Request / Clarification
        if (words.length <= 5 && matchesAny(normalized, isRu ? REPEAT_RU : REPEAT_UZ)) {
            String lastText = s.lastAgentText();
            if (lastText != null && !lastText.isBlank()) {
                String reply = isRu
                        ? "Повторяю: " + lastText
                        : "Takrorlayman: " + lastText;
                log.info("[{}] Fast-path matched: REPEAT", s.channelId());
                return FastPathResult.replyOnly(reply);
            }
        }

        // 6. Identity / Robot Check
        if (words.length <= 6 && matchesAny(normalized, isRu ? IDENTITY_RU : IDENTITY_UZ)) {
            String company = s.companyName();
            boolean hasCompany = company != null && !company.isBlank();
            String identityReply;
            if (isRu) {
                identityReply = hasCompany
                        ? "Я голосовой ассистент компании " + company.trim() + "."
                        : "Я голосовой ассистент.";
            } else {
                identityReply = hasCompany
                        ? "Men " + company.trim() + " kompaniyasining sun'iy intellekt ovozli yordamchisiman."
                        : "Men sun'iy intellekt ovozli yordamchisiman.";
            }
            String lastQ = DialogLines.lastQuestion(s.lastAgentText());
            String reply = lastQ != null ? identityReply + " " + lastQ : identityReply;
            log.info("[{}] Fast-path matched: IDENTITY", s.channelId());
            return FastPathResult.replyOnly(reply);
        }

        // 7. Call Purpose Inquiry
        if (words.length <= 6 && matchesAny(normalized, isRu ? PURPOSE_RU : PURPOSE_UZ)) {
            String purpose = s.scenario() != null ? s.scenario().callPurpose() : null;
            if (purpose != null && !purpose.isBlank()) {
                java.util.Map<String, String> facts = s.context() != null ? s.context().facts() : java.util.Map.of();
                String rendered = PromptTemplateEngine.render(purpose.trim(), facts);
                String purposeReply = isRu
                        ? "Я звоню вам по поводу: " + rendered + "."
                        : "Sizga " + rendered + " masalasida bog'lanyapman.";
                String lastQ = DialogLines.lastQuestion(s.lastAgentText());
                String reply = lastQ != null ? purposeReply + " " + lastQ : purposeReply;
                log.info("[{}] Fast-path matched: PURPOSE", s.channelId());
                return FastPathResult.replyOnly(reply);
            }
        }

        return FastPathResult.NOT_HANDLED;
    }

    private static boolean isClosingGratitude(String normalized, boolean isRu, DialogSession s) {
        if (matchesAny(normalized, isRu ? CLOSING_GRATITUDE_RU : CLOSING_GRATITUDE_UZ)) {
            return true;
        }
        if (s.turnsSinceOutcome() >= 0) {
            String[] words = normalized.split("\\s+");
            if (words.length <= 3 && matchesAny(normalized, isRu ? SIMPLE_GRATITUDE_RU : SIMPLE_GRATITUDE_UZ)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(String input, Set<String> phrases) {
        for (String phrase : phrases) {
            if (input.equals(phrase) || input.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('`', '\'')
                .replace('‘', '\'')
                .replace('’', '\'')
                .replaceAll("[.,!?\\-–—\"«»]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
