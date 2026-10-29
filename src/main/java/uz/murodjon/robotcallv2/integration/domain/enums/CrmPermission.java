package uz.murodjon.robotcallv2.integration.domain.enums;

public enum CrmPermission {
    LEAD,
    LEAD_NOTE,
    LEAD_TASK,
    CONTRACT,
    CONTRACT_PAYMENT,
    BOOKING,
    CLIENT,
    CALL,
    MCP;

    public String wireName() {
        return "PERMISSION_OPEN_API_" + name();
    }
}
