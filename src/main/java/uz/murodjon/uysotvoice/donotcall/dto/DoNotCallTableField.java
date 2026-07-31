package uz.murodjon.uysotvoice.donotcall.dto;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@link DoNotCallFilter} may sort the opt-out list by. */
public enum DoNotCallTableField implements TableField {
    ID("id"),
    PHONE("phone"),
    CREATED_AT("created_at");

    private final String column;

    DoNotCallTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
