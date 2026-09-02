package uz.murodjon.robotcallv2.contact.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns ContactFilter may sort the contact list by. */
public enum ContactTableField implements TableField {
    ID("id"),
    NAME("name"),
    PHONE("phone"),
    CREATED_AT("created_at");

    private final String column;

    ContactTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
