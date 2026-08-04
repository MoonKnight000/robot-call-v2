package uz.murodjon.uysotvoice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates a request by its {@code X-Api-Key} header (PROJECT.md §11 — the API can
 * place real calls, so it must not be reachable anonymously).
 *
 * <p>Two sources, tried in order: the global admin key, then the global read-only key —
 * both unscoped (fallback/bootstrap access, {@code CurrentCompany} defaults them).
 * {@link SecurityConfig} decides which paths and methods each role reaches. The
 * per-company DB-backed key panel (§11 settings, {@code apikey.*}) was removed (report
 * #11 — "API KEYLAR UMUMAN KERAK EMAS"); these two static keys are the only
 * machine-to-machine auth mechanism now.
 *
 * <p>The static-key comparison is constant-time: a plain {@code equals} on a secret leaks
 * its length and prefix to an attacker who can time responses. A blank configured key
 * authenticates nothing, so an unconfigured deployment is closed rather than open.
 *
 * <p>The filter never rejects a request itself — it only populates the security context, so
 * permitted paths still work without a key.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    /** Full access: originate calls, create and start campaigns, opt numbers out. */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    /** Day-to-day operational access — same bar as a JWT-logged-in OPERATOR (ROADMAP E.1). */
    public static final String ROLE_OPERATOR = "ROLE_OPERATOR";
    /** Read-only: reporting, transcripts, recordings, audit log. */
    public static final String ROLE_VIEWER = "ROLE_VIEWER";

    private final byte[] adminKey;
    private final byte[] readKey;

    public ApiKeyFilter(String apiKey, String readApiKey) {
        this.adminKey = bytesOrNull(apiKey);
        this.readKey = bytesOrNull(readApiKey);
    }

    /**
     * {@code /api/live/stream} (SseEmitter) completes via a Servlet ASYNC dispatch on a
     * different thread than the original request. {@link OncePerRequestFilter} skips
     * itself on that second pass by default, so the security context this filter sets
     * would never reach the thread that runs {@code authorizeHttpRequests} for it —
     * every SSE connection would 403 regardless of a valid X-Api-Key.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null) {
            byte[] offered = presented.getBytes(StandardCharsets.UTF_8);
            if (matches(adminKey, offered)) {
                // The admin key also grants the operator and viewer roles, so operational
                // and read endpoints do not have to enumerate every role themselves
                // (ROADMAP E.1 introduced the OPERATOR tier between ADMIN and VIEWER).
                authenticate("api-key", ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER);
            } else if (matches(readKey, offered)) {
                authenticate("read-api-key", ROLE_VIEWER);
            }
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(String principal, String... roles) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, AuthorityUtils.createAuthorityList(roles));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static boolean matches(byte[] expected, byte[] offered) {
        return expected != null && MessageDigest.isEqual(expected, offered);
    }

    private static byte[] bytesOrNull(String key) {
        return (key == null || key.isBlank()) ? null : key.getBytes(StandardCharsets.UTF_8);
    }
}
