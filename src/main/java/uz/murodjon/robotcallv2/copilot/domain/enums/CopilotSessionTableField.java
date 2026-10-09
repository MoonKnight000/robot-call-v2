package uz.murodjon.robotcallv2.copilot.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum CopilotSessionTableField implements TableField {
    UPDATED_AT("updated_at"),
    CREATED_AT("created_at");

    private final String column;

    CopilotSessionTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
