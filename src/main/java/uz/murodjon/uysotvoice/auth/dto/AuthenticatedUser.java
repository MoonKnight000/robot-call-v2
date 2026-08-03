package uz.murodjon.uysotvoice.auth.dto;

import org.springframework.security.core.AuthenticatedPrincipal;

import uz.murodjon.uysotvoice.user.enums.UserRole;

/**
 * The {@code Authentication} principal for a JWT-authenticated request (ROADMAP E.1) —
 * set by {@code config.JwtAuthFilter}, read by {@code auth.service.AuthenticatedCurrentUser}
 * and {@code auth.service.JwtCurrentCompanyResolver} to scope the request to a real user
 * and company instead of the single hardcoded default.
 *
 * <p>{@link #getName()} returns the email rather than the default {@code toString()} so
 * that {@code AuditService.currentActor()} — unchanged — starts recording the real person
 * instead of just an API-key role, for free.
 */
public record AuthenticatedUser(
        long userId,
        long companyId,
        UserRole role,
        String name,
        String email
) implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }
}
