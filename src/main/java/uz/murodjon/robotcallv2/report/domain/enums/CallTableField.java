package uz.murodjon.robotcallv2.report.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns {@link CallFilter} may sort the call report list by. */
public enum CallTableField implements TableField {
    CALL_ID("a.id"),
    STARTED_AT("a.started_at"),
    ENDED_AT("a.ended_at"),
    DURATION_SEC("a.duration_sec"),
    DISPOSITION("a.disposition");

    private final String column;

    CallTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}

