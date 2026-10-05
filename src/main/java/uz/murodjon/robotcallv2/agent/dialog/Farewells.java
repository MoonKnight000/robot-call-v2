package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Set;

/**
 * Closing courtesies in what the agent says — the words that mean the conversation is over.
 *
 * <p>The model writes "Rahmat, kuningiz xayrli o'tsin!" and simply does not call
 * {@code endCall}, and since {@link DialogSession#end} is the only route to the hangup
 * runnable, the goodbye is spoken into a line that then stays open until the caller drops
 * it themselves. The agent's text is written by a model asked for whole sentences, so it
 * is matched on a closing <em>phrase</em> near the end of the line.
 *
 * <p>Only the agent side: whether the <em>caller</em> is signing off is the model's to
 * judge from the conversation ("rahmat, lekin bir savolim bor" is not a goodbye).
 */
final class Farewells {

    /**
     * How far back from the end of an agent line a closing phrase may sit. A goodbye is
     * the last thing said; further back it is a word about goodbyes, not one.
     */
    private static final int AGENT_TAIL_CHARS = 60;

    /** Matched against the tail of an agent line, already normalized the same way it is. */
    private static final Set<String> AGENT_CLOSINGS = Set.of(
            "xayr", "salomat bo ling", "kuningiz xayrli", "kuningiz xayrli o tsin",
            "yaxshi qoling", "sog bo ling", "omon bo ling", "xayrli kun",
            "до свидания", "всего доброго", "всего хорошего", "хорошего дня",
            "будьте здоровы", "до встречи", "всего наилучшего",
            "goodbye", "good bye", "have a good day", "take care");

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
}
