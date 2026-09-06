package uz.murodjon.robotcallv2.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;

/**
 * Turns the tenant of a request into an ordinary method argument, resolved once here at
 * the HTTP edge. Everything below the controller then takes {@code companyId} explicitly,
 * so the same service method serves a REST caller and the dialer's background threads —
 * which carry no security context at all, the thread-local being a servlet concern.
 *
 * <p>There is deliberately no default company to fall back on: a handler reached without
 * an {@link AuthenticatedUser} is refused rather than quietly served one tenant's rows.
 * {@code SecurityConfig} already authenticates every route, so this guard only bites if a
 * future {@code permitAll()} endpoint asks for a company id it cannot have.
 */
public class CurrentCompanyIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentCompanyId.class)
                && parameter.getParameterType() == long.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ForbiddenException(ErrorCode.NO_USER_SESSION);
        }
        return user.companyId();
    }
}
