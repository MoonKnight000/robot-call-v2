package uz.murodjon.robotcallv2.callrecord.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum CallReanalysisTableField implements TableField {
    CREATED_AT("created_at");

    private final String column;

    CallReanalysisTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
