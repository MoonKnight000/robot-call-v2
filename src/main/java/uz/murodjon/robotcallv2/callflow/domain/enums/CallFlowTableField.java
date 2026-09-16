package uz.murodjon.robotcallv2.callflow.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns CallFlowFilter may sort the flow list by. */
public enum CallFlowTableField implements TableField {
    ID("id"),
    NAME("name"),
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at");

    private final String column;

    CallFlowTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
