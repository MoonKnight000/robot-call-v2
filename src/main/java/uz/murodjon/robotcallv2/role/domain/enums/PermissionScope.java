package uz.murodjon.robotcallv2.role.domain.enums;

/**
 * Whether a permission belongs to a tenant or to the platform itself. A company role can
 * only ever hold {@link #COMPANY} permissions — {@link #PLATFORM} ones are what separate
 * platform staff (creating tenants, suspending them) from a tenant's own DEVELOPER, who
 * otherwise holds everything.
 */
public enum PermissionScope {
    COMPANY,
    PLATFORM
}
