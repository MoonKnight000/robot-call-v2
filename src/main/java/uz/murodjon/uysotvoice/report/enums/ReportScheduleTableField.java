package uz.murodjon.uysotvoice.report.enums;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@code ReportScheduleFilter} may sort the schedule list by. */
public enum ReportScheduleTableField implements TableField {
    ID("id"),
    EMAIL("email"),
    PERIODICITY("periodicity"),
    ENABLED("enabled"),
    CREATED_AT("created_at");

    private final String column;

    ReportScheduleTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
