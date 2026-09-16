package uz.murodjon.robotcallv2.schedule.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns HolidayFilter may sort the holiday list by. */
public enum HolidayTableField implements TableField {
    ID("id"),
    DATE("holiday_date", "date"),
    NAME("name"),
    CREATED_AT("created_at");

    private final String column;
    private final String property;

    HolidayTableField(String column) {
        this(column, null);
    }

    HolidayTableField(String column, String property) {
        this.column = column;
        this.property = property;
    }

    @Override
    public String column() {
        return column;
    }

    @Override
    public String property() {
        return property != null ? property : TableField.super.property();
    }
}
