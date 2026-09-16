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
import uz.murodjon.robotcallv2.auth.application.port.input.AccessTokenUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.StreamTicketUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates a request by its {@code Authorization: Bearer <jwt>} header (ROADMAP
 * E.1), or — only for the streaming GETs a browser's {@code EventSource}/{@code <audio>}
 * cannot set a header on — a query parameter: a single-use {@code ?ticket=} from
 * {@code POST /api/auth/stream-ticket}, or (deprecated) the whole access token as
 * {@code ?token=} / {@code ?access_token=}. Everywhere else a credential in the URL is
 * ignored: it would otherwise land in access logs, proxy logs and Referer headers on every
 * request.
 *
 * <p>Each permission the token carries becomes one authority named exactly like the
 * {@link Permission} constant, which is what every {@code @PreAuthorize("hasAuthority(...)")}
 * on the controllers checks.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    /** SSE streams and the live-audio download — the only URLs a query credential is honoured on. */
    private static final List<String> QUERY_CREDENTIAL_SUFFIXES = List.of("/live/stream", "/me/events", "/listen");
    /** The identity a query credential proved, kept on the request for its ASYNC re-dispatch. */
    private static final String QUERY_PRINCIPAL_ATTRIBUTE = JwtAuthFilter.class.getName() + ".queryPrincipal";

    private final AccessTokenUseCase accessTokenUseCase;
    private final StreamTicketUseCase streamTicketUseCase;

    public JwtAuthFilter(AccessTokenUseCase accessTokenUseCase, StreamTicketUseCase streamTicketUseCase) {
        this.accessTokenUseCase = accessTokenUseCase;
        this.streamTicketUseCase = streamTicketUseCase;
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
        AuthenticatedUser user = null;
        if (header != null && header.startsWith(PREFIX)) {
            user = parse(header.substring(PREFIX.length()));
        } else if (acceptsQueryCredential(request)) {
            user = authenticateByQuery(request);
        }

        if (user != null) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null, authorities(user.permissions()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }

    private AuthenticatedUser authenticateByQuery(HttpServletRequest request) {
        // The ASYNC re-dispatch of a stream carries the same URL, but its ticket was spent on
        // the first pass — the identity it proved then is what counts now.
        if (request.getAttribute(QUERY_PRINCIPAL_ATTRIBUTE) instanceof AuthenticatedUser earlier) {
            return earlier;
        }
        AuthenticatedUser user;
        String ticket = request.getParameter("ticket");
        if (ticket != null && !ticket.isBlank()) {
            user = streamTicketUseCase.consume(ticket);
        } else {
            // Deprecated: the whole access token in the URL. Kept until the console moves to tickets.
            String token = request.getParameter("token");
            if (token == null || token.isBlank()) {
                token = request.getParameter("access_token");
            }
            user = token == null ? null : parse(token);
        }
        if (user != null) {
            request.setAttribute(QUERY_PRINCIPAL_ATTRIBUTE, user);
        }
        return user;
    }

    private AuthenticatedUser parse(String token) {
        return token.isBlank() ? null : accessTokenUseCase.parse(token);
    }

    private static boolean acceptsQueryCredential(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return QUERY_CREDENTIAL_SUFFIXES.stream().anyMatch(path::endsWith);
    }

    static List<GrantedAuthority> authorities(Set<Permission> permissions) {
        return permissions.stream()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission.name()))
                .toList();
    }
}
