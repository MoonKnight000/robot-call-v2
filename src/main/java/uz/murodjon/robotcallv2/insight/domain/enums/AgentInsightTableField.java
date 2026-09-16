package uz.murodjon.robotcallv2.insight.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum AgentInsightTableField implements TableField {
    CREATED_AT("created_at"),
    CALL_COUNT("call_count");

    private final String column;

    AgentInsightTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
