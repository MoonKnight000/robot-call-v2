package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.scenario.domain.entity.GlobalNode;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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
            "bezovta qilmang", "spam", "bloklayman", "o'chirib tashlang", "telefon qilmanglar",
            "qo'ng'iroq qilmang", "qo'ng'iroq qilmanglar"
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

    /**
     * A request to be put through, however it is inflected: "operatoringizga ulang", "operatorni ulab bering",
     * "tirik operator bilan ulang". A simulated caller asked that way three times and the model, with the
     * transfer tool on offer, filed the call as COMPLETED. Only the asking forms of the verb, so "operator bilan
     * gaplashgandim" — telling, not asking — does not match.
     */
    private static final Pattern TRANSFER_REQUEST_UZ = Pattern.compile(
            "\\b(operator|mutaxassis|tirik odam|odam)\\S*\\s+(bilan\\s+)?"
                    + "(ulang|ulab|ulasangiz|ulay olasizmi|ulashing|ulanglar|gaplashmoqchiman|gaplashtiring|gaplashay)");

    private static final Pattern TRANSFER_REQUEST_RU = Pattern.compile(
            "(соедин|переве|переключ|свяжите|позовите|дайте)\\S*(\\s+(меня|пожалуйста|срочно))*\\s+(с\\s+|на\\s+)?"
                    + "(жив\\S*\\s+)?(оператор|человек|специалист)");

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
            "qaytadan aytingchi"
    );

    private static final Set<String> REPEAT_RU = Set.of(
            "повторите", "повторите пожалуйста", "что вы сказали", "что ты сказал",
            "не расслышал", "не услышал", "не понял", "плохо слышно"
    );

    /**
     * A repeat request only when it is the whole utterance: these words are also part of ordinary
     * answers ("narxi nima", "а сколько стоит"), and taken as a request they made the agent repeat
     * itself at every answer instead of hearing it.
     */
    private static final Set<String> REPEAT_ALONE_UZ = Set.of("a", "nima");

    private static final Set<String> REPEAT_ALONE_RU = Set.of("что", "а");

    /**
     * A line check — the caller asking whether anyone is there — only when it is the whole
     * utterance: "alo, ha men Murodjonman" is an answer, and the model has to hear it.
     */
    private static final Set<String> LINE_CHECK_UZ = Set.of(
            "alo", "allo", "alyo", "eshityapsizmi", "eshitayapsizmi", "eshityapsanmi", "eshitilyaptimi",
            "eshitib turibsizmi", "alo eshityapsizmi", "allo eshityapsizmi", "alo alo", "allo allo"
    );

    private static final Set<String> LINE_CHECK_RU = Set.of(
            "алло", "алё", "ало", "вы меня слышите", "слышите меня", "слышите", "алло вы меня слышите",
            "алло слышите", "алло алло"
    );

    private static final String REPEAT_PREFIX_UZ = "Takrorlayman: ";

    private static final String REPEAT_PREFIX_RU = "Повторяю: ";

    private static final Set<String> IDENTITY_UZ = Set.of(
            "siz kimsiz", "kim bu", "kim bu o'zi", "kim bu ozi", "robotmisiz", "odammisiz",
            "sun'iy intellektmisiz", "suniy intellektmisiz", "botmisiz", "avtooperator",
            "avtojavobgich", "kim bilan gaplashyapman", "kim bilan gaplashayapman", "siz kimsiz ozi", "siz kimsiz o'zi"
    );

    private static final Set<String> IDENTITY_RU = Set.of(
            "кто это", "кто говорит", "вы робот", "вы человек", "с кем я говорю", "это бот", "вы бот"
    );

    /** The identity questions that ask whether a machine is talking, not who is calling. */
    private static final Set<String> ROBOT_CHECK_UZ = Set.of(
            "robotmisiz", "odammisiz", "sun'iy intellektmisiz", "suniy intellektmisiz", "botmisiz",
            "avtooperator", "avtojavobgich"
    );

    private static final Set<String> ROBOT_CHECK_RU = Set.of("вы робот", "вы человек", "это бот", "вы бот");

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
            String reply = DialogLines.doNotCallReply(s);
            log.info("[{}] Fast-path matched: DO_NOT_CALL", s.channelId());
            return FastPathResult.endWithDisposition(reply, Disposition.DO_NOT_CALL);
        }

        // 3. Human Transfer Request
        // Only where a transfer can happen: with no operator to hand over to, "ulayapman" and a hung-up
        // line is a lie — the model then answers it (@no-transfer rule). A bare "operator" no longer
        // matches: "summa noto'g'ri, operator aytgandi…" is not a request to be transferred.
        if (TransferTargets.isOffered(s.agent(), s.isTransferTargetAvailable(), s.scenario(), s.state())
                && (matchesAny(normalized, isRu ? TRANSFER_RU : TRANSFER_UZ)
                || (isRu ? TRANSFER_REQUEST_RU : TRANSFER_REQUEST_UZ).matcher(normalized).find())) {
            String reply = DialogLines.transferring(s);
            log.info("[{}] Fast-path matched: TRANSFER", s.channelId());
            return FastPathResult.endWithDisposition(reply, Disposition.TRANSFERRED);
        }

        // 4. Closing Gratitude / Polite Farewell
        if (isClosingGratitude(normalized, isRu, s)) {
            String reply = DialogLines.gratitudeReply(s);
            log.info("[{}] Fast-path matched: GRATITUDE_CLOSE", s.channelId());
            Disposition disp = s.disposition() != null ? s.disposition() : Disposition.COMPLETED;
            return FastPathResult.endWithDisposition(reply, disp);
        }

        // 5. Repeat Request / Clarification
        if ((words.length <= 5 && matchesAny(normalized, isRu ? REPEAT_RU : REPEAT_UZ))
                || (isRu ? REPEAT_ALONE_RU : REPEAT_ALONE_UZ).contains(normalized)) {
            String lastText = withoutRepeatPrefix(s.lastAgentText());
            if (lastText != null && !lastText.isBlank()) {
                String reply = (isRu ? REPEAT_PREFIX_RU : REPEAT_PREFIX_UZ) + lastText;
                log.info("[{}] Fast-path matched: REPEAT", s.channelId());
                return FastPathResult.replyOnly(reply);
            }
        }

        // 5b. Line check ("Alo?", "Eshityapsizmi?"): yes, and the question the caller is to answer.
        // Nothing to say before the agent has spoken — the greeting answers that one.
        if ((isRu ? LINE_CHECK_RU : LINE_CHECK_UZ).contains(normalized)) {
            String lastText = withoutRepeatPrefix(s.lastAgentText());
            if (lastText != null && !lastText.isBlank()) {
                String lastQ = DialogLines.lastQuestion(lastText);
                String reply = DialogLines.lineCheckReply(s) + " " + (lastQ != null ? lastQ : lastText);
                log.info("[{}] Fast-path matched: LINE_CHECK", s.channelId());
                return FastPathResult.replyOnly(reply);
            }
        }

        // 6. Identity / Robot Check
        if (words.length <= 6 && matchesAny(normalized, isRu ? IDENTITY_RU : IDENTITY_UZ)) {
            // "Are you a robot?" is answered by what the agent is, never by the company's
            // "who's calling" line, which would dodge it.
            boolean robotCheck = matchesAny(normalized, isRu ? ROBOT_CHECK_RU : ROBOT_CHECK_UZ);
            String identityReply = robotCheck ? null : scenarioIdentityAnswer(s, isRu);
            if (identityReply == null) {
                String company = s.companyName();
                boolean hasCompany = company != null && !company.isBlank();
                if (isRu) {
                    identityReply = hasCompany
                            ? "Я голосовой ассистент компании " + company.trim() + "."
                            : "Я голосовой ассистент.";
                } else {
                    identityReply = hasCompany
                            ? "Men " + company.trim() + " kompaniyasining sun'iy intellekt ovozli yordamchisiman."
                            : "Men sun'iy intellekt ovozli yordamchisiman.";
                }
            }
            String lastQ = DialogLines.lastQuestion(s.lastAgentText());
            String reply = lastQ != null ? identityReply + " " + lastQ : identityReply;
            log.info("[{}] Fast-path matched: IDENTITY", s.channelId());
            return FastPathResult.replyOnly(reply);
        }

        // No fast path for "nima masalada? / tinchlikmi?": answering it IS the next stage's job, and the
        // canned reply read the scenario's callPurpose — an instruction to the model — to the caller verbatim
        // ("Sizga Mijoz Uysot'dan uy olgan… Maqsad — … masalasida bog'lanyapman"), without moving the stage.
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

    /**
     * The scenario's answer to "who are you?" ({@link GlobalNode}), facts filled in, or
     * {@code null} when it has none. A node counts when its own example question is an identity
     * question in the call's language — so the company's wording ("Uysot kompaniyasining to'lovlar
     * bo'limidan...") wins over the generic one, and a node written in another language is not
     * read out on this call.
     */
    private static String scenarioIdentityAnswer(DialogSession s, boolean isRu) {
        if (s.scenario() == null || s.scenario().globalNodes() == null) {
            return null;
        }
        for (GlobalNode node : s.scenario().globalNodes()) {
            if (node.question() != null && node.answer() != null && !node.answer().isBlank()
                    && matchesAny(normalize(node.question()), isRu ? IDENTITY_RU : IDENTITY_UZ)) {
                return PromptTemplateEngine.render(node.answer().strip(), StageEntry.variables(s));
            }
        }
        return null;
    }

    /** Whether one of {@code phrases} occurs in {@code input} as whole words, never as part of a word. */
    private static boolean matchesAny(String input, Set<String> phrases) {
        String padded = " " + input + " ";
        for (String phrase : phrases) {
            if (padded.contains(" " + phrase + " ")) {
                return true;
            }
        }
        return false;
    }

    /** The agent's last line without the "repeating:" lead-ins earlier repeats put in front of it. */
    private static String withoutRepeatPrefix(String text) {
        String line = text;
        while (line != null) {
            if (line.startsWith(REPEAT_PREFIX_UZ)) {
                line = line.substring(REPEAT_PREFIX_UZ.length());
            } else if (line.startsWith(REPEAT_PREFIX_RU)) {
                line = line.substring(REPEAT_PREFIX_RU.length());
            } else {
                break;
            }
        }
        return line;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('`', '\'')
                .replace('‘', '\'')
                .replace('’', '\'')
                // The recognizer writes Uzbek o'/g' with the modifier letter turned comma (U+02BB):
                // "oʻchirib tashlang" never matched the phrase lists below.
                .replace('ʻ', '\'')
                .replace('ʼ', '\'')
                .replaceAll("[.,!?\\-–—\"«»]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
