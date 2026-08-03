package uz.murodjon.uysotvoice.integration.enums;

/** {@code crm_integration.status} (§11 settings). */
public enum CrmIntegrationStatus {
    /** Credentials saved (or nothing saved yet), OAuth dance not completed. */
    NOT_CONNECTED,
    /** Access/refresh tokens on file and usable. */
    CONNECTED,
    /** Token refresh failed — {@link uz.murodjon.uysotvoice.crm.service.CrmClient} falls back to the static config token. */
    ERROR
}
