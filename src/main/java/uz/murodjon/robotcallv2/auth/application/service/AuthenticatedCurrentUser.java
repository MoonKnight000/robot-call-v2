package uz.murodjon.robotcallv2.auth.application.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

import java.util.Optional;

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

    @Override
    public Optional<UserRole> role() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.role());
        }
        return Optional.empty();
    }
}
