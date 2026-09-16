package uz.murodjon.robotcallv2.callflow.domain.entity;

import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowExit;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One step of a call flow. Which fields matter depends on {@link #type()}; the rest stay
 * null (docs/api/call-flows.md lists them per type).
 *
 * @param prompt          what is said, by language tag ("uz-UZ" → text); the call's
 *                        language is picked, then the first entry
 * @param audioFileId     a recording played instead of {@code prompt}
 * @param options         MENU: key ("1", "*") → next node; LANGUAGE_SELECT: key → language tag
 * @param exits           exit key ({@link CallFlowExit#key()}) → next node
 * @param timeoutSec      MENU / LANGUAGE_SELECT: seconds to wait for a key; RING_GROUP,
 *                        EXTENSION, FORWARD_EXTERNAL: seconds to ring
 * @param maxRetries      MENU: how often the menu is repeated after no or a wrong key
 * @param language        LANGUAGE_SELECT: taken when no key is pressed
 * @param skipWhenKnown   LANGUAGE_SELECT: skip the question when the caller's language is known
 * @param extensionDigits MENU: also accept an internal extension of this many digits
 * @param keywords        MENU: words that pick an option when said instead of pressed
 * @param confirmAnswer   FORWARD_EXTERNAL: whoever answers must press 1 before being connected,
 *                        so a mobile's voicemail cannot take the call
 */
public record CallFlowNode(
        CallFlowNodeType type,
        Map<String, String> prompt,
        Long audioFileId,
        Map<String, String> options,
        Map<String, String> exits,
        Integer timeoutSec,
        Integer maxRetries,
        String language,
        Boolean skipWhenKnown,
        Long scheduleId,
        Long queueId,
        Long aiAgentId,
        Long voicemailBoxId,
        List<Long> userIds,
        String phoneNumber,
        Boolean confirmAnswer,
        String url,
        Integer extensionDigits,
        Map<String, List<String>> keywords
) {
    public CallFlowNode {
        prompt = prompt == null ? Map.of() : Map.copyOf(prompt);
        options = options == null ? Map.of() : Map.copyOf(options);
        exits = exits == null ? Map.of() : Map.copyOf(exits);
        userIds = userIds == null ? List.of() : List.copyOf(userIds);
        keywords = keywords == null ? Map.of() : Map.copyOf(keywords);
    }

    /** The node an exit leads to, or null when the flow ends there. */
    public String exit(CallFlowExit exit) {
        return exits.get(exit.key());
    }

    /**
     * The prompt in {@code language}, else in its base language, else the first by language
     * tag — JSONB keeps no key order, so "the first one written" is not knowable after a save.
     */
    public String promptFor(String language) {
        if (prompt.isEmpty()) {
            return null;
        }
        if (language != null) {
            String exact = prompt.get(language);
            if (exact != null) {
                return exact;
            }
            String base = language.length() >= 2 ? language.substring(0, 2) : language;
            for (Map.Entry<String, String> entry : prompt.entrySet()) {
                if (entry.getKey().startsWith(base)) {
                    return entry.getValue();
                }
            }
        }
        return new TreeMap<>(prompt).firstEntry().getValue();
    }
}
