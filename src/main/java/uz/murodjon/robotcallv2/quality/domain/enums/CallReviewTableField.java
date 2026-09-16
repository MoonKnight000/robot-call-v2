package uz.murodjon.robotcallv2.quality.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum CallReviewTableField implements TableField {
    CREATED_AT("created_at"),
    SCORE("score");

    private final String column;

    CallReviewTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
