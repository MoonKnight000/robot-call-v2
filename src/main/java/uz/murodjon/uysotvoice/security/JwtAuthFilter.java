package uz.murodjon.uysotvoice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import uz.murodjon.uysotvoice.auth.dto.AuthenticatedUser;
import uz.murodjon.uysotvoice.auth.service.JwtTokenService;
import uz.murodjon.uysotvoice.user.enums.UserRole;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates a request by its {@code Authorization: Bearer <jwt>} header (ROADMAP
 * E.1) — a real logged-in person, as opposed to {@link ApiKeyFilter}'s machine-to-machine
 * secret. Both filters run in the same chain and never reject on their own, only
 * populate the security context, so a request with neither header simply stays
 * anonymous and falls to {@code SecurityConfig}'s {@code authorizeHttpRequests} rules.
 *
 * <p>Role → authority mapping mirrors a hierarchy: {@code ADMIN} gets every authority
 * {@code OPERATOR} has plus its own, {@code OPERATOR} gets {@code VIEWER}'s plus its
 * own. This is what lets the existing {@code X-Api-Key} admin key (now granting
 * {@code ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER}, see {@link ApiKeyFilter}) keep working
 * against endpoints that moved from {@code hasRole(ADMIN)} to {@code hasRole(OPERATOR)}.
 *
 * <p>{@code SUPERADMIN} (report #3) is deliberately <strong>not</strong> part of that
 * hierarchy — it grants only {@code ROLE_SUPERADMIN}, nothing else. Platform staff
 * managing tenant status/onboarding must not thereby gain access to any tenant's
 * operational data (campaigns, calls, contacts...), which every other role transitively
 * can reach via {@code ROLE_OPERATOR}/{@code ROLE_VIEWER}.
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
        if (header != null && header.startsWith(PREFIX)) {
            AuthenticatedUser user = tokens.parse(header.substring(PREFIX.length()));
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
