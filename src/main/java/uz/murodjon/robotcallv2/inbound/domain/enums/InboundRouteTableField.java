package uz.murodjon.robotcallv2.inbound.domain.enums;

import uz.murodjon.robotcallv2.shared.api.TableField;

/** Columns InboundRouteFilter may sort the route list by. */
public enum InboundRouteTableField implements TableField {
    ID("id"),
    DID_NUMBER("did_number"),
    AI_AGENT_ID("ai_agent_id"),
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
