package uz.murodjon.robotcallv2.contact.domain.enums;

/** The kind of value a contact field holds; fixed once the field is created. */
public enum ContactFieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    /** One of the field's {@code options}. */
    ENUM,
    /** ISO date, {@code 2026-11-06}. */
    DATE,
    /** ISO instant or offset date-time, {@code 2026-11-06T09:00:00Z}. */
    DATETIME
}
