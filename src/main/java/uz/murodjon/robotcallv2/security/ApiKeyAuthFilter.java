package uz.murodjon.robotcallv2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import uz.murodjon.robotcallv2.apikey.application.port.input.ApiKeyUseCase;
import uz.murodjon.robotcallv2.apikey.domain.entity.ApiKey;
import uz.murodjon.robotcallv2.apikey.domain.service.ApiKeyScopes;
import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.io.IOException;
import java.util.Set;

/**
 * Authenticates a request by its {@code X-Api-Key} header — a company's own system calling
 * this API with no person logged in.
 *
 * <p>The identity it builds is an ordinary {@link AuthenticatedUser}, so every
 * {@code @PreAuthorize} on every controller and every {@code @CurrentCompanyId} argument
 * keeps working untouched: a key is simply a caller with fewer permissions. What it holds
 * is its granted scopes intersected with {@link ApiKeyScopes} <em>here</em>, at
 * authentication time, so editing the stored scopes can never hand a key a right that no
 * key is allowed to have.
 *
 * <p>{@code userId} is 0 because nobody is logged in. Anything that needs a real user —
 * the audit trail's actor, a profile — reads null or nothing rather than being told a
 * person did it.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";

    /** What the audit trail and the console show instead of a role name. */
    private static final String ROLE_CODE = "API_KEY";

    private final ApiKeyUseCase apiKeys;

    public ApiKeyAuthFilter(ApiKeyUseCase apiKeys) {
        this.apiKeys = apiKeys;
    }

    /** Same reason as {@code JwtAuthFilter}: an SSE stream completes on an ASYNC dispatch. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        // A Bearer token already answered who is calling; a key header alongside it is
        // ignored rather than allowed to widen or narrow that identity.
        if (presented != null && !presented.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            ApiKey apiKey = apiKeys.findUsableByKey(presented);
            if (apiKey != null) {
                Set<Permission> permissions = ApiKeyScopes.intersect(apiKey.scopes());
                // The key's prefix stands where an email would: the audit trail names the
                // caller by that field, and for a machine the useful name is the thing an
                // operator can go and revoke, not the label somebody typed.
                AuthenticatedUser caller = new AuthenticatedUser(0L, apiKey.companyId(), 0L, ROLE_CODE,
                        permissions, apiKey.name(), apiKey.keyPrefix());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(caller, null,
                                JwtAuthFilter.authorities(permissions)));
            }
        }
        chain.doFilter(request, response);
    }
}
