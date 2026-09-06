package uz.murodjon.robotcallv2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.service.JwtTokenService;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates a request by its {@code Authorization: Bearer <jwt>} header (ROADMAP
 * E.1) or {@code ?token=} / {@code ?access_token=} query parameter (for EventSource/SSE).
 *
 * <p>Each permission the token carries becomes one authority named exactly like the
 * {@link Permission} constant, which is what every {@code @PreAuthorize("hasAuthority(...)")}
 * on the controllers checks.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenService tokens;

    public JwtAuthFilter(JwtTokenService tokens) {
        this.tokens = tokens;
    }

    /**
     * {@code /api/live/stream} (SseEmitter) completes via a Servlet ASYNC dispatch on a
     * different thread than the original request. {@link OncePerRequestFilter} skips
     * itself on that second pass by default, so the security context this filter sets
     * would never reach the thread that runs {@code authorizeHttpRequests} for it —
     * every SSE connection would 403 regardless of a valid Bearer token.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        String token = null;
        if (header != null && header.startsWith(PREFIX)) {
            token = header.substring(PREFIX.length());
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            token = request.getParameter("token");
        } else if (request.getParameter("access_token") != null && !request.getParameter("access_token").isBlank()) {
            token = request.getParameter("access_token");
        }

        if (token != null && !token.isBlank()) {
            AuthenticatedUser user = tokens.parse(token);
            if (user != null) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, authorities(user.permissions()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    static List<GrantedAuthority> authorities(Set<Permission> permissions) {
        return permissions.stream()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.name()))
                .toList();
    }
}
