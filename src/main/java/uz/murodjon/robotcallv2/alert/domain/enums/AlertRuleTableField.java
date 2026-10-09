package uz.murodjon.robotcallv2.alert.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum AlertRuleTableField implements TableField {
    CREATED_AT("created_at"),
    NAME("name"),
    LAST_TRIGGERED_AT("last_triggered_at");

    private final String column;

    AlertRuleTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
