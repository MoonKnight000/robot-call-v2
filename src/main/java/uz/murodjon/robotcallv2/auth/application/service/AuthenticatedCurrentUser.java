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

    @Override
    public Optional<Long> id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
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
