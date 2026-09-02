package uz.murodjon.robotcallv2.campaign.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns {@link TargetFilter} may sort the campaign target list by. */
public enum TargetTableField implements TableField {
    ID("id"),
    PHONE("phone"),
    STATUS("status"),
    ATTEMPTS("attempts");

    private final String column;

    TargetTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
