package uz.murodjon.robotcallv2.siptrunk.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns SipTrunkFilter may sort the trunk list by. */
public enum SipTrunkTableField implements TableField {
    ID("id"),
    NAME("name"),
    IS_DEFAULT("is_default"),
    ENABLED("enabled"),
    CREATED_AT("created_at");

    private final String column;

    SipTrunkTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
