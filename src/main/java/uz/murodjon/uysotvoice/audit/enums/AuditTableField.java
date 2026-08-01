package uz.murodjon.uysotvoice.audit.enums;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@link AuditFilter} may sort the audit log by. */
public enum AuditTableField implements TableField {
    ID("id"),
    CREATED_AT("created_at"),
    ACTION("action"),
    ENTITY("entity");

    private final String column;

    AuditTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
