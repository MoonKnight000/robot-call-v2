package uz.murodjon.robotcallv2.missedcall.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns MissedCallFilter may sort the missed-call list by. */
public enum MissedCallTableField implements TableField {
    ID("id"),
    LAST_AT("last_at"),
    FIRST_AT("first_at"),
    CALL_COUNT("call_count");

    private final String column;

    MissedCallTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
