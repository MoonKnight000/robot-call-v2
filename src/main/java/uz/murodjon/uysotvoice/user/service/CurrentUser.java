package uz.murodjon.uysotvoice.user.service;

import uz.murodjon.uysotvoice.user.enums.UserRole;

import java.util.Optional;

/**
 * The {@code app_user} making the current request, if any (ROADMAP E.1) — mirrors
 * {@link uz.murodjon.uysotvoice.company.service.CurrentCompany}. Empty for requests
 * authenticated by the machine-to-machine {@code X-Api-Key}, which has no associated
 * person to name.
 */
public interface CurrentUser {

    Optional<Long> id();

    /**
     * The caller's role, if a JWT-authenticated person made this request — used by
     * {@code company.service.CompanyAccessGuard} to let {@code SUPERADMIN} bypass the
     * usual "own company only" scoping (report #3). Empty for {@code X-Api-Key}
     * requests, same as {@link #id()} — a machine key is never a superadmin.
     */
    Optional<UserRole> role();
}
