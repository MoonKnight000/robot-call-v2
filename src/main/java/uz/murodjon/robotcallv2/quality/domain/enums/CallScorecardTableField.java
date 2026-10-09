package uz.murodjon.robotcallv2.quality.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns CallScorecardFilter may sort the scorecard inbox by. */
public enum CallScorecardTableField implements TableField {
    CREATED_AT("created_at"),
    SCORE("score");

    private final String column;

    CallScorecardTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
