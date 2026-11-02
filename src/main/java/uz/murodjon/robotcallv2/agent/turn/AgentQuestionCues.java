package uz.murodjon.robotcallv2.agent.turn;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads the agent's last line for the kind of answer it asked for (VOICE-QUALITY-PLAN A.1).
 *
 * <p>The context half of what a turn-taking model does: "ikki xonali" after "necha xonali?"
 * is a whole answer, the same two words after "nima sababdan?" are the start of one. Only
 * the last question in the line counts — the one the caller is answering — and an open
 * marker beats a closed one, because misreading an open question cuts the caller off while
 * misreading a closed one only costs the wait it would have cost anyway.
 *
 * <p>Kept narrow on purpose. "qanday"/"как" are left out: "Qanday to'lamoqchisiz?" is
 * answered in a word, "Как это случилось?" is not. Pure functions, no Spring.
 */
public final class AgentQuestionCues {

    private static final Pattern APOSTROPHES = Pattern.compile("['`‘’ʻʼ]");
    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,;:.!?…\"«»()-]+");

    /** Asks for a reason or an account — the answer is a sentence or more. */
    private static final List<String> OPEN_MARKERS = List.of(
            "nima uchun", "nega", "nimaga", "nima sabab", "nima boldi", "tushuntirib", "batafsil",
            "aytib bering", "gapirib bering",
            "почему", "зачем", "расскажите", "что случилось", "объясните", "подробнее");

    /** A question word whose answer is a pick: a number, a name, one of the options given. */
    private static final List<String> CLOSED_WORDS = List.of(
            "necha", "nechta", "qaysi", "yoki",
            "сколько", "какой", "какая", "какое", "какие", "какую", "который", "которая", "или");

    private AgentQuestionCues() {
    }

    /**
     * @param agentLine everything the agent said in its last turn, as written
     */
    public static AgentQuestion classify(String agentLine) {
        String question = lastQuestion(agentLine);
        if (question == null) {
            return AgentQuestion.NONE;
        }
        String normalized = APOSTROPHES.matcher(question.toLowerCase(Locale.ROOT)).replaceAll("");
        for (String marker : OPEN_MARKERS) {
            if (normalized.contains(marker)) {
                return AgentQuestion.OPEN;
            }
        }
        String[] words = WORD_SPLIT.split(normalized.trim());
        for (String word : words) {
            if (CLOSED_WORDS.contains(word)) {
                return AgentQuestion.CLOSED;
            }
        }
        String last = words.length == 0 ? "" : words[words.length - 1];
        // The Uzbek yes/no particle: "bormi", "qulaymi", "qiziqtiradimi"; Russian "ли".
        boolean yesNo = last.length() >= 4 && last.endsWith("mi") || "ли".equals(last)
                || words.length >= 2 && "ли".equals(words[1]);
        return yesNo ? AgentQuestion.CLOSED : AgentQuestion.NONE;
    }

    /** The last sentence of {@code line} that ends in a question mark, or null when none does. */
    private static String lastQuestion(String line) {
        if (line == null) {
            return null;
        }
        int end = line.lastIndexOf('?');
        if (end < 0) {
            return null;
        }
        int start = end - 1;
        while (start >= 0 && ".!?…".indexOf(line.charAt(start)) < 0) {
            start--;
        }
        String sentence = line.substring(start + 1, end).trim();
        return sentence.isEmpty() ? null : sentence;
    }
}
