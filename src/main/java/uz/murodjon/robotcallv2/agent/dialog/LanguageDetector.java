package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * High-accuracy language detector for Uzbek and Russian caller speech (PROJECT.md §7).
 *
 * <p>One campaign dials Uzbek and Russian clients from the same target list, and the
 * language on the target row is frequently unverified or defaulted in the CRM. The call is
 * opened in the campaign's language, and this detector notices when the caller answers
 * in the other language — prompting the engine to switch voice, system prompt, and STT
 * parameters smoothly.
 *
 * <p>Heuristic Rules:
 * <ul>
 *   <li><b>Uzbek Latin:</b> Transcribed in Latin alphabet (a-z) with Uzbek phonetics -> {@code uz}.
 *   <li><b>Uzbek Cyrillic:</b> Contains Uzbek-exclusive Cyrillic letters ({@code ў, қ, ғ, ҳ}) or
 *       Uzbek vocabulary -> {@code uz}.
 *   <li><b>Russian Cyrillic:</b> Contains Russian-exclusive letters ({@code ы, щ}) or Russian
 *       distinctive vocabulary without Uzbek markers -> {@code ru}.
 *   <li><b>Ambiguous / Shared:</b> Shared loan words ("bank", "karta", "nomi") or very short
 *       utterances ("alo") remain undecided ({@code null}) to prevent false flip-flops.
 * </ul>
 */
@Component
public class LanguageDetector {

    /** Minimum alphanumeric characters to evaluate confident language detection. */
    static final int MIN_LETTERS = 5;

    /** Letters exclusive to Russian and absent from standard Uzbek Cyrillic. */
    private static final String RUSSIAN_ONLY_CHARS = "\u044b\u0449\u042b\u0429"; // ы, щ, Ы, Щ

    /** Letters exclusive to Uzbek Cyrillic and absent from Russian. */
    private static final String UZBEK_ONLY_CHARS = "\u045e\u049b\u0493\u04b3\u040e\u049a\u0492\u04b2"; // ў, қ, ғ, ҳ, Ў, Қ, Ғ, Ҳ

    /** Shared loan or ambiguous words that alone should not trigger a language switch. */
    private static final Set<String> AMBIGUOUS_WORDS = Set.of(
            "alo", "allo", "ha", "da", "bank", "karta", "nomi", "kartochka", "kartasi", "krediti"
    );

    /** Core Uzbek words (Latin & Cyrillic) to avoid misclassification as Russian. */
    private static final Set<String> UZBEK_WORDS = Set.of(
            // Latin
            "ha", "yoq", "yo'q", "rahmat", "ertaga", "bugun", "tolayman", "to'layman",
            "qarz", "qarzim", "tushundim", "mayli", "hop", "xop", "eshitaman", "eshityapman",
            "kim", "nima", "qancha", "qachon", "siz", "men", "biz", "aka", "uka", "opa",
            "ozim", "o'zim", "boladi", "bo'ladi", "qilaman", "beraman", "kartaga", "hisobga",
            "gapiring", "salom", "assalomu", "alaykum", "valaykum", "murodjon", "shartnoma", "tolov", "to'lov",

            // Cyrillic
            "ҳа", "йўқ", "раҳмат", "эртага", "бугун", "тўлайман", "толайман", "тўлай", "толай",
            "қарз", "қарзим", "тушундим", "майли", "хўп", "хоп", "эшитаман", "эшитяпман",
            "ким", "нима", "қанча", "қачон", "сиз", "мен", "биз", "ака", "ука", "опа",
            "ўзим", "озим", "бўлади", "болади", "қиламан", "бераман", "картага", "ҳисобга",
            "гапиринг", "салом", "ассалому", "алайкум", "валайкум", "муроджон", "шартнома", "тўлов", "толов"
    );

    /** Distinctive Russian words. */
    private static final Set<String> RUSSIAN_WORDS = Set.of(
            "да", "нет", "не", "я", "вы", "мы", "он", "она", "это", "как", "кто", "где",
            "когда", "почему", "сколько", "деньги", "долг", "оплатить", "оплачу", "здравствуйте",
            "спасибо", "хорошо", "понял", "поняла", "слушаю", "говорите", "перезвоните",
            "завтра", "сегодня", "карту", "договор", "счет", "номер", "пожалуйста", "зачем",
            "платить", "хочу", "буду", "могу"
    );

    /**
     * Detects language of {@code text}, returning matched candidate subtag or {@code null}.
     */
    public String detect(String text, Collection<String> candidates) {
        if (text == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String clean = text.trim();
        if (clean.length() < MIN_LETTERS) {
            return null;
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (AMBIGUOUS_WORDS.contains(lower)) {
            return null;
        }

        int latinCount = 0;
        int cyrillicCount = 0;
        boolean uzbekCharFound = false;
        boolean russianCharFound = false;

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (UZBEK_ONLY_CHARS.indexOf(c) >= 0) {
                uzbekCharFound = true;
            }
            if (RUSSIAN_ONLY_CHARS.indexOf(c) >= 0) {
                russianCharFound = true;
            }
            if (c >= 'a' && c <= 'z') {
                latinCount++;
            } else if (c >= 0x0400 && c <= 0x04FF) {
                cyrillicCount++;
            }
        }

        if (latinCount + cyrillicCount < MIN_LETTERS) {
            return null;
        }

        // 1. Uzbek-specific Cyrillic letters (ў, қ, ғ, ҳ) are conclusive
        if (uzbekCharFound) {
            return match(candidates, "uz");
        }

        // 2. Predominant Latin script indicates Uzbek in this domain
        if (latinCount > cyrillicCount * 2) {
            return match(candidates, "uz");
        }

        // 3. Predominant Cyrillic script
        if (cyrillicCount > latinCount * 2) {
            boolean hasUzbekWord = containsWord(lower, UZBEK_WORDS);
            if (hasUzbekWord) {
                return match(candidates, "uz");
            }

            boolean hasRussianWord = containsWord(lower, RUSSIAN_WORDS);
            if (russianCharFound || hasRussianWord) {
                return match(candidates, "ru");
            }
        }

        return null;
    }

    private static boolean containsWord(String text, Set<String> dictionary) {
        String[] tokens = text.split("[^\\p{L}']+");
        for (String token : tokens) {
            if (token != null && !token.isBlank() && dictionary.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Matches candidate starting with given subtag (e.g. 'uz' -> 'uz-UZ' or 'uz'). */
    private static String match(Collection<String> candidates, String subtag) {
        for (String candidate : candidates) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(subtag)) {
                return candidate;
            }
        }
        return null;
    }
}
