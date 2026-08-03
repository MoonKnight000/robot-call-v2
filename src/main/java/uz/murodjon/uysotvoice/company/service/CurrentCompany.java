package uz.murodjon.uysotvoice.company.service;

import uz.murodjon.uysotvoice.company.config.CompanyProperties;

/**
 * The company the current unit of work belongs to (ROADMAP Bosqich B).
 *
 * <p>Real per-request resolution exists via {@code auth.service.JwtCurrentCompanyResolver}
 * — a JWT login, or a per-company DB-backed {@code X-Api-Key} (backend-uchun-talablar.md
 * §8, {@code apikey.service.ApiKeyService}/{@code security.ApiKeyFilter}), both resolve to
 * the real company. {@link DefaultCompanyResolver} — a single hardcoded id — is now only
 * the fallback for the two global bootstrap keys (admin/read-only), which stay
 * intentionally unscoped. Every repository that filters by company depends on this
 * interface rather than either resolver directly, so that fallback never leaks into
 * call sites that should always see the real company.
 */
public interface CurrentCompany {

    long id();
}
