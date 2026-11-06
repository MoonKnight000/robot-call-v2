package uz.murodjon.robotcallv2.contact.domain.service;

import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateAction;
import uz.murodjon.robotcallv2.contact.domain.enums.ContactFieldUpdateMode;

/** Pure rules for how a value extracted after a call meets the value a contact already holds. */
public final class ContactFieldUpdatePolicy {

    private ContactFieldUpdatePolicy() {
    }

    public static ContactFieldUpdateAction decide(ContactFieldUpdateMode mode, String stored, String incoming) {
        if (incoming == null || incoming.isBlank()) {
            return ContactFieldUpdateAction.SKIP;
        }
        boolean empty = stored == null || stored.isBlank();
        return switch (mode == null ? ContactFieldUpdateMode.OVERWRITE : mode) {
            case OVERWRITE -> incoming.equals(stored) ? ContactFieldUpdateAction.SKIP : ContactFieldUpdateAction.WRITE;
            case FILL_IF_EMPTY -> empty ? ContactFieldUpdateAction.WRITE : ContactFieldUpdateAction.SKIP;
            // Already holding the new text verbatim means this call was merged before (an outbox re-run).
            case ACCUMULATE -> empty ? ContactFieldUpdateAction.WRITE
                    : stored.contains(incoming) ? ContactFieldUpdateAction.SKIP : ContactFieldUpdateAction.MERGE;
        };
    }

    /**
     * Both values kept, the new one below the stored one, for when the model could not merge
     * them. Over {@code maxLength} the oldest text is dropped, so the latest call always survives.
     */
    public static String appendBelow(String stored, String incoming, int maxLength) {
        String joined = stored.strip() + "\n\n" + incoming.strip();
        return fit(joined, maxLength);
    }

    /** {@code value} cut to its last {@code maxLength} characters. */
    public static String fit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(value.length() - maxLength).strip();
    }
}
