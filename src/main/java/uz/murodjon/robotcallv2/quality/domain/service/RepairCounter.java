package uz.murodjon.robotcallv2.quality.domain.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Counts the caller's repair requests in a transcript — "what?", "I didn't understand", "say
 * that again" (UNIVERSAL-OPERATOR-PLAN D.1). Read from the words, without an LLM: several on one
 * call mean the agent's speech did not land, whatever the judge made of its content.
 */
public final class RepairCounter {

    private static final String CLIENT_PREFIX = "CLIENT:";

    private static final List<String> REPAIR_PHRASES = List.of(
            "nima dedingiz", "tushunmadim", "qaytaring", "qaytadan ayting", "yana bir marta",
            "eshitilmayapti", "eshitmadim",
            "что вы сказали", "не понял", "не поняла", "повторите", "ещё раз", "еще раз", "не слышно");

    /** A whole utterance that is only a "what?" or a "hello?". */
    private static final Set<String> BARE_REPAIRS = Set.of("nima", "a", "allo", "alo", "что", "алло");

    private RepairCounter() {
    }

    /** One per caller line that asks for a repair; 0 for no transcript. */
    public static int countRepairs(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : transcript.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.regionMatches(true, 0, CLIENT_PREFIX, 0, CLIENT_PREFIX.length())) {
                continue;
            }
            String said = trimmed.substring(CLIENT_PREFIX.length()).strip().toLowerCase(Locale.ROOT);
            String bare = said.replaceAll("[\\p{Punct}\\s]+", "");
            if (BARE_REPAIRS.contains(bare) || REPAIR_PHRASES.stream().anyMatch(said::contains)) {
                count++;
            }
        }
        return count;
    }
}
