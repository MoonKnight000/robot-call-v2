package uz.murodjon.robotcallv2.scenario.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns ScenarioFilter may sort the scenario list by. */
public enum ScenarioTableField implements TableField {
    ID("id"),
    SCENARIO_KEY("scenario_key"),
    NAME("name"),
    VERSION("version"),
    CREATED_AT("created_at");

    private final String column;

    ScenarioTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
