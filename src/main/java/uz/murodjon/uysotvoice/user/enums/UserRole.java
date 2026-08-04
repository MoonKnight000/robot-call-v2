package uz.murodjon.uysotvoice.user.enums;

/**
 * {@code app_user.role} (ROADMAP E.1). Maps to Spring Security authorities in
 * {@code security.JwtAuthFilter}: {@code ADMIN} implies {@code OPERATOR} implies
 * {@code VIEWER}.
 *
 * <p>{@code SUPERADMIN} is platform staff, not a tenant role — deliberately
 * <strong>not</strong> chained to {@code ADMIN}/{@code OPERATOR}/{@code VIEWER} (see
 * {@code JwtAuthFilter#authorities}), so it can manage tenants (company status,
 * onboarding — report #3) without also gaining access to any tenant's operational
 * data. Never settable through the tenant-scoped {@code /api/users/**} endpoints
 * ({@code UserService#invite}/{@code #changeRole} reject it) — the first superadmin
 * account is a one-time manual {@code app_user} row, not a self-service invite.
 */
public enum UserRole {
    ADMIN,
    OPERATOR,
    VIEWER,
    SUPERADMIN
}
