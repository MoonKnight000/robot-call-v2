package uz.murodjon.robotcallv2.promise.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum PaymentPromiseTableField implements TableField {
    CREATED_AT("created_at"),
    PROMISED_DATE("promised_date");

    private final String column;

    PaymentPromiseTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
