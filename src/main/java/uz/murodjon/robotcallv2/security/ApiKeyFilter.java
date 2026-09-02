package uz.murodjon.robotcallv2.security;

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
 * Authenticates a request by its {@code X-Api-Key} header or {@code ?apiKey=} / {@code ?api_key=} query parameter.
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
        if (presented == null || presented.isBlank()) {
            presented = request.getParameter("apiKey");
            if (presented == null || presented.isBlank()) {
                presented = request.getParameter("api_key");
            }
        }

        if (presented != null) {
            byte[] offered = presented.getBytes(StandardCharsets.UTF_8);
            if (matches(adminKey, offered)) {
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
