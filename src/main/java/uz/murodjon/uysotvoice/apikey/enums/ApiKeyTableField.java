package uz.murodjon.uysotvoice.apikey.enums;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@code ApiKeyFilter} may sort the key list by. */
public enum ApiKeyTableField implements TableField {
    ID("id"),
    NAME("name"),
    ROLE("role"),
    CREATED_AT("created_at"),
    LAST_USED_AT("last_used_at");

    private final String column;

    ApiKeyTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
