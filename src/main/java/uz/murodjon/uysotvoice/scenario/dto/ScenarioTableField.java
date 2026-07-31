package uz.murodjon.uysotvoice.scenario.dto;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@link ScenarioFilter} may sort the scenario list by. */
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
