package uz.murodjon.uysotvoice.inbound.enums;

import uz.murodjon.uysotvoice.shared.api.TableField;

/** Columns {@code InboundRouteFilter} may sort the route list by. */
public enum InboundRouteTableField implements TableField {
    ID("id"),
    DID_NUMBER("did_number"),
    LANGUAGE("language"),
    ENABLED("enabled"),
    CREATED_AT("created_at");

    private final String column;

    InboundRouteTableField(String column) {
        this.column = column;
    }

    @Override
    public String column() {
        return column;
    }
}
