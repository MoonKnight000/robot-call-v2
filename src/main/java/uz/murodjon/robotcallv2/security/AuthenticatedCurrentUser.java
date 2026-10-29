package uz.murodjon.robotcallv2.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

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

    @Override
    public String actorName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "system";
        }
        if (auth.getPrincipal() instanceof AuthenticatedUser user) {
            String identifier = user.email() != null && !user.email().isBlank() ? user.email() : ("user:" + user.userId());
            return user.roleCode() != null ? identifier + " (" + user.roleCode() + ")" : identifier;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || name.startsWith("AuthenticatedUser[")) {
            return "system";
        }
        return name;
    }

    @Override
    public Optional<String> remoteAddress() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return Optional.empty();
        }
        return Optional.ofNullable(servletAttrs.getRequest().getRemoteAddr());
    }

    @Override
    public Optional<String> userAgent() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return Optional.empty();
        }
        String userAgent = servletAttrs.getRequest().getHeader("User-Agent");
        return Optional.ofNullable(userAgent).map(value -> value.substring(0, Math.min(value.length(), 255)));
    }

    @Override
    public Runnable wrap(Runnable action) {
        if (action == null) {
            return () -> {};
        }
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(context);
                action.run();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        };
    }
}
