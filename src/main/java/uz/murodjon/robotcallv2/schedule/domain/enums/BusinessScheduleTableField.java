package uz.murodjon.robotcallv2.schedule.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns BusinessScheduleFilter may sort the schedule list by. */
public enum BusinessScheduleTableField implements TableField {
    ID("id"),
    NAME("name"),
    CREATED_AT("created_at");

    private final String column;

    BusinessScheduleTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
