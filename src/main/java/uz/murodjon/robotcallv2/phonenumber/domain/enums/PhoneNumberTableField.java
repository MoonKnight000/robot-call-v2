package uz.murodjon.robotcallv2.phonenumber.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

public enum PhoneNumberTableField implements TableField {
    ID("id"),
    PHONE_NUMBER("phone_number"),
    CATEGORY("category"),
    MONTHLY_FEE("monthly_fee"),
    STATUS("status"),
    CREATED_AT("created_at");

    private final String column;

    PhoneNumberTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
