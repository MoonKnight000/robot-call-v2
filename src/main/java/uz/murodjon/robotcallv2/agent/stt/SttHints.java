package uz.murodjon.robotcallv2.agent.stt;

import uz.murodjon.robotcallv2.agent.dialog.CallContext;

import java.util.*;

/**
 * The words this particular call is likely to contain, handed to the recognizer before
 * anybody says anything.
 *
 * <p>A recognizer has no idea it is about to hear "Rustamova Dilnoza" or "Payme", and the
 * words it gets wrong are exactly the ones the call turns on: the client's own name, the
 * contract number they are asked to quote, the app they are told to pay through. Every
 * major API takes a list of expected terms for this, and until now none of ours was given
 * one.
 *
 * <p>Two sources, both cheap. The <b>call's own facts</b> — whatever the scenario declared
 * and the CRM filled in — are the per-call half, and the only half that can contain a
 * name. The <b>fixed vocabulary</b> below is the half every call in this market shares:
 * payment services and institutions that a general-purpose model has never been trained
 * on, and mis-hears as ordinary words.
 *
 * <p>Numbers are left out on purpose. A contract number arrives as digits, the caller says
 * it as words, and the two do not help each other — {@code UzbekNumberParser} is what
 * bridges that.
 */
public final class SttHints {

    /** How many terms are worth sending. Beyond this the list stops steering and starts biasing. */
    private static final int MAX_HINTS = 65;

    /**
     * Names a general model has never seen and mis-hears as ordinary words: "Payme" as
     * "pay me", "Uzum" as "uzun", MIB as three unrelated letters.
     */
    private static final List<String> DOMAIN_TERMS = List.of(
            "Payme", "Click", "Uzum", "Uzum Bank", "Anor Bank", "Kapitalbank", "Ipoteka Bank",
            "Hamkorbank", "Agrobank", "Xalq banki", "TBC", "TBC Bank", "Paynet", "Humo", "Uzcard",
            "Infinbank", "Aloqabank", "Asakabank", "SQB", "Turonbank", "Mikrokreditbank",
            "MIB", "Majburiy ijro byurosi", "shartnoma raqami", "shartnoma", "qarzdorlik",
            "kechiktirilgan to'lov", "muddatli to'lov", "penya", "foiz", "jadval", "kvitansiya",
            "Пейми", "Клик", "Узум", "Анорбанк", "Хумо", "Узкард", "задолженность", "договор", "график"
    );

    private SttHints() {
    }

    /**
     * Terms to bias this call's recognition towards, most specific first.
     *
     * @param context the call's facts, or {@code null} for a call with none (an inbound
     *                caller nobody recognized) — the shared vocabulary still applies
     */
    public static List<String> of(CallContext context) {
        Set<String> hints = new LinkedHashSet<>();
        if (context != null) {
            for (Map.Entry<String, Object> fact : context.facts().entrySet()) {
                addWords(hints, fact.getValue());
            }
        }
        hints.addAll(DOMAIN_TERMS);
        return hints.size() <= MAX_HINTS
                ? List.copyOf(hints)
                : List.copyOf(new ArrayList<>(hints).subList(0, MAX_HINTS));
    }

    /**
     * Add a fact's value if it is the kind of thing a recognizer can be steered by: text,
     * not a sum or a date. A name is added whole and word by word, because callers answer
     * with either.\n     */
    private static void addWords(Set<String> hints, Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 2 || trimmed.chars().anyMatch(Character::isDigit)) {
            return;
        }
        hints.add(trimmed);
        for (String word : trimmed.split("\\s+")) {
            if (word.length() > 2) {
                hints.add(word);
            }
        }
    }
}
