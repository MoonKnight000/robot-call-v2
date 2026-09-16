package uz.murodjon.robotcallv2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.ratelimit.RateLimiter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Per-address ceilings on the endpoints that need no token: login, refresh, password
 * recovery and the public widget. Runs before authentication, so a flood never reaches the
 * password check or the database, and answers {@code 429 RATE_LIMITED} in the same
 * {@code ResponseData} envelope every other error uses — written by hand, because a
 * servlet filter sits outside the dispatcher the exception handler advises.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String BODY = "{\"data\":null,\"message\":\"" + ErrorCode.RATE_LIMITED.format()
            + "\",\"messageCode\":\"" + ErrorCode.RATE_LIMITED.name() + "\",\"accept\":false,\"errors\":null}";

    private record Rule(String name, int limit, Duration window) {
    }

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Rule rule = properties.enabled() ? ruleFor(request) : null;
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        String address = clientAddress(request);
        if (rateLimiter.tryAcquire("rl:" + rule.name() + ":" + address, rule.limit(), rule.window())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("rate limit {} exceeded by {} on {} {}", rule.name(), address, request.getMethod(),
                request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", String.valueOf(rule.window().toSeconds()));
        response.getWriter().write(BODY);
    }

    private Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean post = "POST".equalsIgnoreCase(request.getMethod());
        if (post && path.equals("/api/auth/login")) {
            return new Rule("login", properties.loginPerMinute(), Duration.ofMinutes(1));
        }
        if (post && path.equals("/api/auth/refresh")) {
            return new Rule("refresh", properties.refreshPerMinute(), Duration.ofMinutes(1));
        }
        if (post && (path.equals("/api/auth/forgot-password") || path.equals("/api/auth/reset-password"))) {
            return new Rule("password-reset", properties.passwordResetPerHour(), Duration.ofHours(1));
        }
        if (path.startsWith("/api/public/widgets/")) {
            return new Rule("widget", properties.publicWidgetPerMinute(), Duration.ofMinutes(1));
        }
        return null;
    }

    private String clientAddress(HttpServletRequest request) {
        return ClientAddressArgumentResolver.resolve(request, properties.trustForwardedFor());
    }
}
