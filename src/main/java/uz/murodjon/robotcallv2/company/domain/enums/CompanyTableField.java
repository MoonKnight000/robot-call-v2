package uz.murodjon.robotcallv2.company.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns CompanyFilter may sort the company list by. */
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
