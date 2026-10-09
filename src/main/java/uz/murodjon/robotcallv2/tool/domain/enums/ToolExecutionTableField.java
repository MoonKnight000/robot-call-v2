package uz.murodjon.robotcallv2.tool.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum ToolExecutionTableField implements TableField {
    CREATED_AT("created_at"),
    LATENCY_MS("latency_ms");

    private final String column;

    ToolExecutionTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
