package uz.murodjon.uysotvoice.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.auth.dto.AuthenticatedUser;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.util.Optional;

/**
 * {@link CurrentUser} backed by the JWT principal {@code config.JwtAuthFilter} sets.
 * Empty when the request is authenticated by {@code X-Api-Key} instead — that path has
 * no person to name.
 */
@Component
public class AuthenticatedCurrentUser implements CurrentUser {

    @Override
    public Optional<Long> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.userId());
        }
        return Optional.empty();
    }
}
