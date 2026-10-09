package uz.murodjon.robotcallv2.quality.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns ScorecardCriterionFilter may sort the criteria by. */
public enum ScorecardCriterionTableField implements TableField {
    ID("id"),
    NAME("name"),
    WEIGHT("weight"),
    CREATED_AT("created_at");

    private final String column;

    ScorecardCriterionTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
