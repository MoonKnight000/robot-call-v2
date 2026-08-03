package uz.murodjon.uysotvoice.user.enums;

/**
 * {@code app_user.role} (ROADMAP E.1). Maps to Spring Security authorities in
 * {@code config.JwtAuthFilter}: {@code ADMIN} implies {@code OPERATOR} implies
 * {@code VIEWER}.
 */
public enum UserRole {
    ADMIN,
    OPERATOR,
    VIEWER
}
