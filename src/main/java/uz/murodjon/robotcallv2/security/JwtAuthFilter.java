package uz.murodjon.robotcallv2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.service.JwtTokenService;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request by its {@code Authorization: Bearer <jwt>} header (ROADMAP
 * E.1) or {@code ?token=} / {@code ?access_token=} query parameter (for EventSource/SSE).
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
                var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities(user.role()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private static List<GrantedAuthority> authorities(UserRole role) {
        return switch (role) {
            case ADMIN -> AuthorityUtils.createAuthorityList("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_VIEWER");
            case OPERATOR -> AuthorityUtils.createAuthorityList("ROLE_OPERATOR", "ROLE_VIEWER");
            case VIEWER -> AuthorityUtils.createAuthorityList("ROLE_VIEWER");
            case SUPERADMIN -> AuthorityUtils.createAuthorityList("ROLE_SUPERADMIN");
        };
    }
}
