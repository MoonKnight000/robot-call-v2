package uz.murodjon.robotcallv2.agent.turn;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based end-of-turn cues from the transcript text, for the languages the acoustic
 * turn model does not cover (VOICE-QUALITY-PLAN A.1, C.4).
 *
 * <p>The problem it addresses is the overlap the C.4 dataset exposed: mid-sentence pauses
 * ran 204–1824 ms and finished-turn pauses started at ~1100 ms, so no single silence
 * threshold separates "thinking" from "done". What separates them in Uzbek and Russian
 * is largely the last word. Uzbek is verb-final: a sentence that has reached its finite
 * verb ("to'layman", "bo'ladi", "bilmadim") is almost always over, while one that stops on
 * a case-marked noun ("pulni", "bankga"), a converb ("borib", "bo'lsa"), a conjunction or
 * a bare number has its verb still to come. Russian is freer, but a preposition, a
 * conjunction or a number at the end is just as clearly unfinished, and a finite verb or
 * a "да/нет/хорошо" just as clearly finished. This is what LiveKit's turn detector and
 * Vapi's smart endpointing do with a model; until uz-UZ has one, the rules stand in.
 *
 * <p>The verdict feeds {@code SpeechGate}: {@link TurnCue#COMPLETE} lets the gate close
 * sooner, {@link TurnCue#INCOMPLETE} makes it wait longer, {@link TurnCue#UNKNOWN} leaves
 * the timer alone. Both error directions matter, but not equally — an INCOMPLETE that was
 * wrong costs a few hundred milliseconds of silence, a COMPLETE that was wrong cuts the
 * caller off — so the COMPLETE cues are kept to forms that end a sentence and little
 * else, and an INCOMPLETE ending always beats a COMPLETE one on the same word.
 *
 * <p>The script of the last word picks the lexicon, not the call's configured language:
 * callers mix Uzbek and Russian more often than a CRM row admits, and a Cyrillic word on
 * an uz-UZ call is still a word this can read. Pure functions, no Spring.
 */
public final class TranscriptTurnCues {

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,;:.!?…\"«»()]+");
    private static final Pattern DIGITS = Pattern.compile("\\d+([.,]\\d+)?%?");
    private static final Pattern APOSTROPHES = Pattern.compile("['`‘’ʻʼ-]");

    // ---------------------------------------------------------------- Uzbek
    // Latin script, apostrophes stripped: to'g'ri → togri, yo'q → yoq, o'n → on.

    /** Words that only ever introduce more: conjunctions, postpositions, bare pronouns, numbers. */
    private static final Set<String> UZ_INCOMPLETE_WORDS = Set.of(
            // conjunctions and connectors
            "chunki", "lekin", "ammo", "biroq", "agar", "va", "yoki", "balki", "hamda", "keyin", "song",
            "masalan", "yani", "deganda", "bolsa", "bolganda", "uchun", "bilan", "haqida", "kabi",
            "deb", "degan", "shuning", "shunda", "unda", "endi", "faqat", "yana", "hali", "hatto",
            // a subject or demonstrative with nothing after it yet
            "men", "biz", "siz", "sen", "u", "bu", "shu", "osha", "mana", "ana",
            // numerals — an amount or a date being dictated
            "bir", "ikki", "uch", "tort", "besh", "olti", "yetti", "sakkiz", "toqqiz", "on",
            "yigirma", "ottiz", "qirq", "ellik", "oltmish", "yetmish", "sakson", "toqson",
            "yuz", "ming", "million", "milliard", "yarim"
    );

    /** Words that close an answer on their own. */
    private static final Set<String> UZ_COMPLETE_WORDS = Set.of(
            "ha", "yoq", "xop", "mayli", "boldi", "bopti", "rahmat", "kerak", "mumkin", "emas",
            "edi", "ekan", "shunday", "togri", "yaxshi", "tushunarli", "tushundim", "bilmadim",
            "bilmayman", "albatta", "aniq", "hozircha", "ertaga", "indinga", "bugun", "kechqurun",
            "ertalab", "eshitaman", "eshityapman", "som", "dollar", "kun", "hafta", "oy"
    );

    /**
     * Finite verb, copula and imperative endings — the sentence has reached its verb.
     * Deliberately absent: the second-person possessive {@code -ingiz} ("pulingiz" is a
     * noun waiting for its verb, not "kutingiz") and the question particle {@code -mi},
     * which the recognizer cannot tell from the possessive in "raqami", "ismi".
     */
    private static final List<String> UZ_COMPLETE_SUFFIXES = List.of(
            "yapman", "yapmiz", "yapsiz", "yapti", "yaptilar",
            "moqchiman", "moqchimiz", "moqchisiz", "moqchi",
            "ganman", "ganmiz", "gansiz", "ganlar", "kanman", "qanman",
            "dim", "dik", "ding", "dingiz", "dilar", "di",
            "man", "miz", "aman", "amiz", "asiz", "adi", "ydi", "ydilar",
            "ing", "sin"
    );

    /** Case endings and converbs — a noun or clause whose verb has not come yet. */
    private static final List<String> UZ_INCOMPLETE_SUFFIXES = List.of(
            "ning", "ni", "ga", "ka", "qa", "da", "dan",
            "ib", "gach", "kach", "qach", "guncha", "kuncha", "quncha",
            "sa", "sam", "sang", "sak", "sangiz", "salar", "ganda", "gandan", "gani"
    );

    // -------------------------------------------------------------- Russian

    private static final Set<String> RU_INCOMPLETE_WORDS = Set.of(
            // conjunctions, particles and prepositions
            "и", "а", "но", "или", "что", "чтобы", "потому", "если", "когда", "как", "где",
            "который", "которая", "которые", "в", "во", "на", "с", "со", "по", "к", "ко", "у",
            "о", "об", "обо", "для", "из", "от", "до", "за", "про", "через", "при", "без",
            "над", "под", "между", "ну", "вот", "то", "не", "ни", "значит", "например",
            "просто", "уже", "ещё", "еще", "тоже", "также",
            // a subject with nothing after it yet
            "я", "мы", "вы", "ты", "он", "она", "они",
            // numerals
            "один", "одна", "два", "две", "три", "четыре", "пять", "шесть", "семь", "восемь",
            "девять", "десять", "двадцать", "тридцать", "сорок", "пятьдесят", "сто", "двести",
            "тысяч", "тысяча", "тысячи", "миллион", "миллиона", "полтора"
    );

    private static final Set<String> RU_COMPLETE_WORDS = Set.of(
            "да", "нет", "хорошо", "ладно", "понятно", "ясно", "спасибо", "конечно", "наверное",
            "всё", "все", "точно", "правильно", "нормально", "завтра", "послезавтра", "сегодня",
            "вечером", "утром", "сум", "сумов", "долларов", "рублей", "дней", "неделю", "неделе",
            "месяц", "месяце"
    );

    /**
     * Finite verbs, infinitives and imperatives. Endings nouns share too often at the end
     * of a phrase ({@code -ит} кредит, {@code -ат} результат, {@code -ут} маршрут,
     * {@code -им} режим) are left out: a missed COMPLETE costs silence, a false one costs
     * the caller's sentence.
     */
    private static final List<String> RU_COMPLETE_SUFFIXES = List.of(
            "ться", "ть", "лся", "лась", "лись", "лось",
            "ешь", "ёшь", "ете", "ёте", "ите", "йте",
            "ем", "ём", "ет", "ёт", "ют", "ят", "ишь",
            "ла", "ло", "ли", "лю", "аю", "яю", "ую", "ню", "чу", "гу", "ду", "ту", "жу", "шу", "щу", "ру", "ну"
    );

    private TranscriptTurnCues() {
    }

    /**
     * @param transcript the recognizer's latest hypothesis for the utterance in progress
     */
    public static TurnCue judge(String transcript) {
        String last = lastWord(transcript);
        if (last == null) {
            return TurnCue.UNKNOWN;
        }
        if (DIGITS.matcher(last).matches()) {
            return TurnCue.INCOMPLETE;
        }
        return isCyrillic(last) ? judgeRussian(last) : judgeUzbek(last);
    }

    /** How many words the hypothesis holds — a one- or two-word COMPLETE is a quick answer. */
    public static int wordCount(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String word : WORD_SPLIT.split(transcript.trim())) {
            if (!word.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static TurnCue judgeUzbek(String word) {
        if (UZ_INCOMPLETE_WORDS.contains(word)) {
            return TurnCue.INCOMPLETE;
        }
        if (UZ_COMPLETE_WORDS.contains(word)) {
            return TurnCue.COMPLETE;
        }
        // A suffix only means something on a word long enough to carry a stem in front
        // of it — "di" on its own is not a past tense.
        if (word.length() < 4) {
            return TurnCue.UNKNOWN;
        }
        // "-ning" (genitive) has to beat "-ing" (imperative) and "-ganda" has to beat
        // "-da": the longer, more specific ending wins, and a tie goes to INCOMPLETE.
        int incompleteAt = longestSuffix(word, UZ_INCOMPLETE_SUFFIXES);
        int completeAt = longestSuffix(word, UZ_COMPLETE_SUFFIXES);
        if (incompleteAt > 0 && incompleteAt >= completeAt) {
            return TurnCue.INCOMPLETE;
        }
        return completeAt > 0 ? TurnCue.COMPLETE : TurnCue.UNKNOWN;
    }

    private static TurnCue judgeRussian(String word) {
        if (RU_INCOMPLETE_WORDS.contains(word)) {
            return TurnCue.INCOMPLETE;
        }
        if (RU_COMPLETE_WORDS.contains(word)) {
            return TurnCue.COMPLETE;
        }
        if (word.length() < 4) {
            return TurnCue.UNKNOWN;
        }
        return longestSuffix(word, RU_COMPLETE_SUFFIXES) > 0 ? TurnCue.COMPLETE : TurnCue.UNKNOWN;
    }

    /** Length of the longest listed suffix {@code word} ends with (leaving a stem), or 0. */
    private static int longestSuffix(String word, List<String> suffixes) {
        int best = 0;
        for (String suffix : suffixes) {
            if (suffix.length() > best && word.length() > suffix.length() && word.endsWith(suffix)) {
                best = suffix.length();
            }
        }
        return best;
    }

    /** The last word, lower-cased, apostrophes and hyphens dropped ("to'g'ri" → "togri"). */
    private static String lastWord(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return null;
        }
        String[] words = WORD_SPLIT.split(transcript.trim().toLowerCase(Locale.ROOT));
        for (int i = words.length - 1; i >= 0; i--) {
            String word = APOSTROPHES.matcher(words[i]).replaceAll("");
            if (!word.isEmpty()) {
                return word;
            }
        }
        return null;
    }

    private static boolean isCyrillic(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.UnicodeBlock.of(word.charAt(i)) == Character.UnicodeBlock.CYRILLIC) {
                return true;
            }
        }
        return false;
    }
}
