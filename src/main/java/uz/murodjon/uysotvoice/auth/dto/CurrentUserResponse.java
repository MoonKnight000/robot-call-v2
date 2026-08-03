package uz.murodjon.uysotvoice.auth.dto;

import uz.murodjon.uysotvoice.user.enums.UserRole;

/**
 * {@code GET /api/auth/me} (UI-DESIGN §7–9: sidebar company switcher, employee card,
 * topbar). Flat rather than nesting a {@code Company} object — the panel only ever
 * needs the id/name pair to render, not the full company settings.
 */
public record CurrentUserResponse(
        long userId,
        String name,
        String username,
        String email,
        UserRole role,
        long companyId,
        String companyName
) {
}
