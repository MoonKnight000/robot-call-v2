package uz.murodjon.robotcallv2.user.domain.enums;

/**
 * Distinguishes between platform administrators and tenant (company) users.
 * PLATFORM_ADMIN has no company_id or company role, and manages the platform as a whole.
 * COMPANY_USER belongs to a single company and holds a role within that company.
 */
public enum UserType {
    PLATFORM_ADMIN,
    COMPANY_USER
}
