package uz.murodjon.robotcallv2.crm.domain.enums;

/** How the last CRM contact sync run ended. */
public enum CrmContactSyncStatus {
    SUCCESS,
    /** The CRM refused or could not be reached; {@code lastError} says why. */
    FAILED,
    /** The company has no CRM connection to read from. */
    NOT_CONNECTED,
    /** The connected CRM cannot list its contacts. */
    UNSUPPORTED
}
