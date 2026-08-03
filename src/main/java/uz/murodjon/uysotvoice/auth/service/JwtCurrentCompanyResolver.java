package uz.murodjon.uysotvoice.auth.service;

import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.auth.dto.AuthenticatedUser;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.company.service.DefaultCompanyResolver;

/**
 * {@link CurrentCompany} resolved from the {@link AuthenticatedUser} principal on the
 * request — set by {@code security.JwtAuthFilter} for a real JWT login (ROADMAP E.1),
 * and equally by {@code security.ApiKeyFilter} for a per-company DB-backed {@code
 * X-Api-Key} (backend-uchun-talablar.md §8, ROADMAP B.2) — both produce the same
 * principal shape, so this resolver scopes either kind of request correctly with no
 * branching of its own. Falls back to {@link DefaultCompanyResolver} only for the two
 * global bootstrap keys (admin/read-only), which carry no per-company principal by
 * design and stay scoped to the single hardcoded default company.
 */
@Component
@Primary
public class JwtCurrentCompanyResolver implements CurrentCompany {

    private final DefaultCompanyResolver fallback;

    public JwtCurrentCompanyResolver(DefaultCompanyResolver fallback) {
        this.fallback = fallback;
    }

    @Override
    public long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.companyId();
        }
        return fallback.id();
    }
}
