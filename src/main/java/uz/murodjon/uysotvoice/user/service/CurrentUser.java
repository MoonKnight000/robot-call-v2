package uz.murodjon.uysotvoice.user.service;

import java.util.Optional;

/**
 * The {@code app_user} making the current request, if any (ROADMAP E.1) — mirrors
 * {@link uz.murodjon.uysotvoice.company.service.CurrentCompany}. Empty for requests
 * authenticated by the machine-to-machine {@code X-Api-Key}, which has no associated
 * person to name.
 */
public interface CurrentUser {

    Optional<Long> id();
}
