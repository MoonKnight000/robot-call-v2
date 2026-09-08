package uz.murodjon.robotcallv2.aiagent.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns {@link uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter} may sort the agent list by. */
public enum AiAgentTableField implements TableField {
    ID("id"),
    NAME("name"),
    LANGUAGE("language"),
    SCENARIO_ID("scenario_id", "scenario.id"),
    CREATED_AT("created_at");

    private final String column;
    private final String property;

    AiAgentTableField(String column) {
        this(column, null);
    }

    /** {@code property} where the entity maps the column as an association, not a plain field. */
    AiAgentTableField(String column, String property) {
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
