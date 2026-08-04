package uz.murodjon.uysotvoice.company.service;

import uz.murodjon.uysotvoice.company.config.CompanyProperties;

/**
 * The company the current unit of work belongs to (ROADMAP Bosqich B).
 *
 * <p>Real per-request resolution exists via {@code auth.service.JwtCurrentCompanyResolver}
 * for a JWT login, which resolves to the real company. {@link DefaultCompanyResolver} —
 * a single hardcoded id — is the fallback for the two global bootstrap {@code
 * X-Api-Key} secrets (admin/read-only, {@code security.ApiKeyFilter}), which stay
 * intentionally unscoped (the per-company DB-backed key panel this used to also cover
 * was removed, report #11). Every repository that filters by company depends on this
 * interface rather than either resolver directly, so that fallback never leaks into
 * call sites that should always see the real company.
 */
public interface CurrentCompany {

    long id();
}
