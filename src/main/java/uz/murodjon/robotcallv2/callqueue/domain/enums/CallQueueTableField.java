package uz.murodjon.robotcallv2.callqueue.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns CallQueueFilter may sort the queue list by. */
public enum CallQueueTableField implements TableField {
    ID("id"),
    NAME("name"),
    CREATED_AT("created_at");

    private final String column;

    CallQueueTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
