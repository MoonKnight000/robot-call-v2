package uz.murodjon.robotcallv2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Authenticates a request by its {@code X-Api-Key} header or {@code ?apiKey=} / {@code ?api_key=} query parameter.
 *
 * <p>A machine caller has no role row, so its authorities come straight from the
 * {@link Permission} catalog: the full key gets everything a company role can hold, the
 * read-only key only the {@code *_READ} half. Neither ever gets
 * {@link Permission#PLATFORM_ADMIN} — creating or suspending a tenant stays a human action.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

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
                authenticate("api-key", Permission.findCompanyPermissions());
            } else if (matches(readKey, offered)) {
                authenticate("read-api-key", Permission.findCompanyReadPermissions());
            }
        }
        chain.doFilter(request, response);
    }

    private static void authenticate(String principal, Set<Permission> permissions) {
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, JwtAuthFilter.authorities(permissions));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static boolean matches(byte[] expected, byte[] offered) {
        return expected != null && MessageDigest.isEqual(expected, offered);
    }

    private static byte[] bytesOrNull(String key) {
        return (key == null || key.isBlank()) ? null : key.getBytes(StandardCharsets.UTF_8);
    }
}
