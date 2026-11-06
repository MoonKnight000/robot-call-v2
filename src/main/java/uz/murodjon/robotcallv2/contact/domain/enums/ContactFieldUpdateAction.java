package uz.murodjon.robotcallv2.contact.domain.enums;

/** What becomes of one extracted value once its field's update mode has been applied. */
public enum ContactFieldUpdateAction {
    /** The stored value stays. */
    SKIP,
    /** The extracted value replaces the stored one. */
    WRITE,
    /** The stored and the extracted value are merged into one (ACCUMULATE). */
    MERGE
}
