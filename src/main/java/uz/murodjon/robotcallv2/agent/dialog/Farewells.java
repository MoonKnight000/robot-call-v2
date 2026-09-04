package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * Closing courtesies — the words that mean the conversation is over, as opposed to the
 * words that carry an answer.
 *
 * <p>Read at both ends of the call, for two halves of the same failure. From the agent:
 * the model writes "Rahmat, kuningiz xayrli o'tsin!" and simply does not call
 * {@code endCall}, and since {@link DialogSession#end} is the only route to the hangup
 * runnable, the goodbye is spoken into a line that then stays open until the caller drops
 * it themselves. From the caller: a "rahmat" once the outcome is already recorded is not a
 * question, and putting it through a turn costs a "bir soniya" filler, an LLM round trip
 * and a second goodbye on top of the one they just heard.
 *
 * <p>The two directions are matched differently on purpose. The agent's text is written by
 * a model asked for whole sentences, so it is matched on a closing <em>phrase</em> near the
 * end of the line. The caller's is a recognizer transcript, so it follows the
 * {@link Backchannels} contract instead: short, and made of nothing but these words.
 */
final class Farewells {

    /**
     * How far back from the end of an agent line a closing phrase may sit. A goodbye is
     * the last thing said; further back it is a word about goodbyes, not one.
     */
    private static final int AGENT_TAIL_CHARS = 60;

    /** Longest caller utterance that can be nothing but a sign-off ("katta rahmat sizga"). */
    private static final int CALLER_MAX_WORDS = 3;

    /** Matched against the tail of an agent line, already normalized the same way it is. */
    private static final Set<String> AGENT_CLOSINGS = Set.of(
            "xayr", "salomat bo ling", "kuningiz xayrli", "kuningiz xayrli o tsin",
            "yaxshi qoling", "sog bo ling", "omon bo ling", "xayrli kun",
            "до свидания", "всего доброго", "всего хорошего", "хорошего дня",
            "будьте здоровы", "до встречи", "всего наилучшего",
            "goodbye", "good bye", "have a good day", "take care");

    /**
     * Words that make an utterance a sign-off on their own. At least one has to be there —
     * otherwise "до" or "you" alone would end a call.
     */
    private static final Set<String> CALLER_CORE = Set.of(
            "rahmat", "raxmat", "rahmet", "raxmet", "rahmatlar", "xayr", "salomat", "qoling",
            "рахмат", "рахмет", "раҳмат", "спасибо", "спс", "благодарю", "свидания", "пока",
            "thanks", "thank", "goodbye", "bye");

    /** Words that may keep a sign-off company without carrying it. */
    private static final Set<String> CALLER_FILLERS = Set.of(
            "katta", "sizga", "ham", "juda", "boling", "yaxshi", "mayli",
            "вам", "тебе", "большое", "до", "всего", "доброго", "хорошего", "удачи", "и",
            "you", "very", "much", "so");

    private Farewells() {
    }

    /**
     * Whether an agent line ends the conversation.
     *
     * <p>A line that ends in a question never does, however it is worded: the model asked
     * for something and is owed an answer.
     */
    static boolean saidByAgent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.strip();
        if (trimmed.charAt(trimmed.length() - 1) == '?') {
            return false;
        }
        String normalized = TranscriptText.normalize(trimmed);
        String tail = normalized.length() > AGENT_TAIL_CHARS
                ? normalized.substring(normalized.length() - AGENT_TAIL_CHARS)
                : normalized;
        for (String closing : AGENT_CLOSINGS) {
            if (tail.contains(closing)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a caller transcript is a sign-off and nothing else. */
    static boolean saidByCaller(String text) {
        if (text == null) {
            return false;
        }
        String[] words = Backchannels.words(text);
        if (words.length == 0 || words.length > CALLER_MAX_WORDS) {
            return false;
        }
        boolean core = false;
        for (String word : words) {
            if (CALLER_CORE.contains(word)) {
                core = true;
            } else if (!CALLER_FILLERS.contains(word)) {
                return false;
            }
        }
        return core;
    }
}
