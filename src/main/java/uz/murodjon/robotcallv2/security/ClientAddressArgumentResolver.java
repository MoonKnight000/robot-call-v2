package uz.murodjon.robotcallv2.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link ClientAddress} parameters. The address is taken exactly as
 * {@link RateLimitFilter} takes it — the first {@code X-Forwarded-For} hop only when
 * {@code voice-agent.security.rate-limit.trust-forwarded-for} says a proxy overwrites that
 * header — so the per-address request limit and the login lockout agree on who "an
 * address" is.
 */
public class ClientAddressArgumentResolver implements HandlerMethodArgumentResolver {

    private final RateLimitProperties rateLimitProperties;

    public ClientAddressArgumentResolver(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ClientAddress.class)
                && parameter.getParameterType() == String.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return resolve(request.getNativeRequest(HttpServletRequest.class), rateLimitProperties.trustForwardedFor());
    }

    static String resolve(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
