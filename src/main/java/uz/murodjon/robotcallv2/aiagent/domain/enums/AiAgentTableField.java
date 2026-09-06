package uz.murodjon.robotcallv2.aiagent.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns {@link uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter} may sort the agent list by. */
public enum AiAgentTableField implements TableField {
    ID("id"),
    NAME("name"),
    LANGUAGE("language"),
    SCENARIO_ID("scenario_id"),
    CREATED_AT("created_at");

    private final String column;

    AiAgentTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
