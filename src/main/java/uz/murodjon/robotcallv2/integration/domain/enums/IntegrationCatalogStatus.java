package uz.murodjon.robotcallv2.integration.domain.enums;

/** What a catalog entry means to the company looking at it. */
public enum IntegrationCatalogStatus {
    /** Can be connected now. */
    AVAILABLE,
    /** Planned, no connector yet. */
    COMING_SOON,
    /** Exists, but the platform has switched it off for this company. */
    DISABLED_BY_PLATFORM
}
