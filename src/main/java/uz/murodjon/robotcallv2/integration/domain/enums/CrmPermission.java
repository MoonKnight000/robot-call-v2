package uz.murodjon.robotcallv2.integration.domain.enums;

public enum CrmPermission {
    LEAD,
    LEAD_NOTE,
    LEAD_TASK,
    CONTRACT,
    CONTRACT_PAYMENT,
    CALL;

    public String wireName() {
        return "PERMISSION_OPEN_API_" + name();
    }
}
