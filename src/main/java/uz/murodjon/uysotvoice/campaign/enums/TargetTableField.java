package uz.murodjon.uysotvoice.campaign.enums;

import uz.murodjon.uysotvoice.shared.api.TableField;

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
