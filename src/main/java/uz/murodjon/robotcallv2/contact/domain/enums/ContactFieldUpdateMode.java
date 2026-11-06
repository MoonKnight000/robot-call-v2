package uz.murodjon.robotcallv2.contact.domain.enums;

/** How a value extracted after a call meets the value the contact already holds. */
public enum ContactFieldUpdateMode {
    /** The new value always replaces the stored one. */
    OVERWRITE,
    /** Written only while the contact has no value; later calls leave it alone. */
    FILL_IF_EMPTY,
    /** An LLM merges the stored and the new value, guided by the field's description (TEXT only). */
    ACCUMULATE
}
