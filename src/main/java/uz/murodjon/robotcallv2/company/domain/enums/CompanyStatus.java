package uz.murodjon.robotcallv2.company.domain.enums;

/** company.status (ROADMAP B.1). Anything but ACTIVE is closed to logins, API keys and calls. */
public enum CompanyStatus {
    ACTIVE,
    SUSPENDED,
    /** Soft delete: shut like SUSPENDED, hidden from the default list, data kept and restorable. */
    DELETED,
    /** The platform-set end date passed (CompanyExpiryJob); closed like SUSPENDED, back to ACTIVE by extending it. */
    EXPIRED
}
