package uz.murodjon.uysotvoice.integration.enums;

/**
 * One of Uysot's Open API permission classes (report #10 — {@code CrmGrant}, the
 * building block of the {@code grants} param {@code CrmIntegrationService} sends to
 * Uysot's authorize endpoint). Named after our own API's shorter form; {@link
 * #wireName()} is Uysot's actual {@code PERMISSION_OPEN_API_*} constant.
 */
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
