package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.util.Optional;

@Component
public class AuthenticatedCurrentUser implements CurrentUser {

    /**
     * @return the {@code app_user} behind this request, or empty when there is none.
     *
     * <p>Empty covers two cases that mean the same thing to a caller: no identity at all,
     * and an identity that is not a person — an API key authenticates as its company with
     * a {@code userId} of 0. Answering 0 there would let a key reach the endpoints that
     * act on "my" rows (profile, notifications, sessions) and act on a user that does not
     * exist; empty makes them refuse it, which is what a machine credential deserves.
     */
    @Override
    public Optional<Long> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && user.userId() > 0) {
            return Optional.of(user.userId());
        }
        return Optional.empty();
    }

    @Override
    public boolean hasPermission(Permission permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(authority -> permission.name().equals(authority.getAuthority()));
    }
}
