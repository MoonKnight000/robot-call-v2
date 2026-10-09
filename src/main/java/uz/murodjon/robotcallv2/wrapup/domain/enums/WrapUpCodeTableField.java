package uz.murodjon.robotcallv2.wrapup.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns WrapUpCodeFilter may sort the code list by. */
public enum WrapUpCodeTableField implements TableField {
    ID("id"),
    NAME("name"),
    OUTCOME("outcome"),
    CREATED_AT("created_at");

    private final String column;

    WrapUpCodeTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
