package uz.murodjon.uysotvoice.config;

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
 * <p>Two keys, two roles. The admin key authorizes everything; the optional read-only key
 * authorizes reporting and nothing else. They exist separately because the people who need
 * call results are not the people who need to originate calls, and a single shared secret
 * forced anyone reading a transcript to hold the key that can dial every debtor on the list.
 * {@link SecurityConfig} decides which paths and methods each role reaches.
 *
 * <p>The comparison is constant-time: a plain {@code equals} on a secret leaks its length
 * and prefix to an attacker who can time responses. A blank configured key authenticates
 * nothing, so an unconfigured deployment is closed rather than open.
 *
 * <p>The filter never rejects a request itself — it only populates the security context, so
 * permitted paths still work without a key.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    /** Full access: originate calls, create and start campaigns, opt numbers out. */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    /** Read-only: reporting, transcripts, recordings, audit log. */
    public static final String ROLE_VIEWER = "ROLE_VIEWER";

    private final byte[] adminKey;
    private final byte[] readKey;

    public ApiKeyFilter(String apiKey, String readApiKey) {
        this.adminKey = bytesOrNull(apiKey);
        this.readKey = bytesOrNull(readApiKey);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null) {
            byte[] offered = presented.getBytes(StandardCharsets.UTF_8);
            if (matches(adminKey, offered)) {
                // The admin key also grants the viewer role, so read endpoints do not have
                // to enumerate both.
                authenticate("api-key", ROLE_ADMIN, ROLE_VIEWER);
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
