package uz.murodjon.robotcallv2.operator.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns OperatorFilter may sort the operator list by. */
public enum OperatorTableField implements TableField {
    USER_ID("user_id"),
    NAME("name"),
    STATUS("status");

    private final String column;

    OperatorTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
