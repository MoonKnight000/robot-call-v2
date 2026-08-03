package uz.murodjon.uysotvoice.company.enums;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@code CompanyFilter} may sort the company list by. */
public enum CompanyTableField implements TableField {
    ID("id"),
    NAME("name"),
    STATUS("status"),
    CREATED_AT("created_at");

    private final String column;

    CompanyTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
