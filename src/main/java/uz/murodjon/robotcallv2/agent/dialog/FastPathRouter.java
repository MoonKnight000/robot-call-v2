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
            "bezovta qilmang", "spam", "bloklayman"
    );

    private static final Set<String> DO_NOT_CALL_RU = Set.of(
            "не звоните больше", "не звоните", "удалите мой номер", "удалите номер", "не беспокойте"
    );

    private static final Set<String> TRANSFER_UZ = Set.of(
            "operatorga ula", "operatorga ulang", "operator bilan gaplashmoqchiman",
            "operator chaqir", "odam bilan gaplashay", "tirik odam bormi", "mutaxassisga ulang"
    );

    private static final Set<String> TRANSFER_RU = Set.of(
            "соедините с оператором", "переведите на оператора", "хочу поговорить с человеком",
            "дайте оператора", "живой человек есть", "свяжите с оператором"
    );

    public FastPathResult evaluate(DialogSession s, String clientText) {
        if (clientText == null || clientText.isBlank() || s.isEnded()) {
            return FastPathResult.NOT_HANDLED;
        }

        String normalized = normalize(clientText);
        boolean isRu = s.language() != null && s.language().startsWith("ru");

        // 1. Wrong person / wrong number detection
        if (matchesAny(normalized, isRu ? WRONG_PERSON_RU : WRONG_PERSON_UZ)) {
            String reply = isRu
                    ? "Извините, мы ошиблись номером. Всего доброго."
                    : "Kechirasiz, adashibmiz. Xayr, salomat bo'ling.";
            log.info("[{}] Fast-path matched: WRONG_NUMBER ('{}')", s.channelId(), clientText);
            return FastPathResult.endWithDisposition(reply, Disposition.WRONG_NUMBER);
        }

        // 2. Do Not Call / Opt-out
        if (matchesAny(normalized, isRu ? DO_NOT_CALL_RU : DO_NOT_CALL_UZ)) {
            String reply = isRu
                    ? "Понятно, ваш номер исключен из базы. Извините за беспокойство."
                    : "Tushundim, raqamingiz ro'yxatdan chiqariladi. Bezovta qilganimiz uchun uzr.";
            log.info("[{}] Fast-path matched: DO_NOT_CALL ('{}')", s.channelId(), clientText);
            return FastPathResult.endWithDisposition(reply, Disposition.DO_NOT_CALL);
        }

        // 3. Human Transfer Request
        if (matchesAny(normalized, isRu ? TRANSFER_RU : TRANSFER_UZ)) {
            String reply = DialogPhrases.transferring(s.language());
            log.info("[{}] Fast-path matched: TRANSFER ('{}')", s.channelId(), clientText);
            return FastPathResult.endWithDisposition(reply, Disposition.TRANSFERRED);
        }

        return FastPathResult.NOT_HANDLED;
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
                .replaceAll("[.,!?\\-–—'\"]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
