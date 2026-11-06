package uz.murodjon.robotcallv2.chat.domain.service;

import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.util.List;
import java.util.Map;

/**
 * Who the person on the other end of a chat is, as far as the conversation's recorded values
 * tell: a phone they shared (Telegram's contact button, the app's facts, a tool that asked) and
 * the name a CRM lookup or the app put next to it. A chat has no caller id, so this is all there is.
 */
public final class ChatParticipant {

    /** The keys a phone arrives under — the channel's own, the app's facts, a scenario's field. */
    public static final List<String> PHONE_KEYS = List.of("phone", "clientPhone", "client_phone", "phoneNumber",
            "phone_number", "callerNumber", "contactPhone");

    /** The keys a name arrives under — a CRM lookup's, the app's facts. */
    public static final List<String> NAME_KEYS = List.of("clientName", "client_name", "name", "fullName",
            "full_name");

    private ChatParticipant() {
    }

    /** The person's number in E.164, or null when none of the values is one. */
    public static String findPhone(Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return null;
        }
        for (String key : PHONE_KEYS) {
            Object value = outcome.get(key);
            if (value != null && !value.toString().isBlank()) {
                String normalized = PhoneNumbers.normalize(value.toString());
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    /** The person's name, or null when nothing recorded one. */
    public static String findName(Map<String, Object> outcome) {
        if (outcome == null || outcome.isEmpty()) {
            return null;
        }
        for (String key : NAME_KEYS) {
            Object value = outcome.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return null;
    }
}
