package uz.murodjon.robotcallv2.inbound.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns InboundCallerRuleFilter may sort the rule list by. */
public enum CallerRuleTableField implements TableField {
    ID("id"),
    PHONE_PREFIX("phone_prefix"),
    CREATED_AT("created_at");

    private final String column;

    CallerRuleTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
