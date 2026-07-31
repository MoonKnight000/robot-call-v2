package uz.murodjon.uysotvoice.company.service;

import uz.murodjon.uysotvoice.company.config.CompanyProperties;

/**
 * The company the current unit of work belongs to (ROADMAP Bosqich B).
 *
 * <p>Backed today by {@link DefaultCompanyResolver} — a single hardcoded id, since
 * there is no per-request identity yet (no user accounts, one shared {@code X-Api-Key}
 * per role; ROADMAP E.1). Every repository that filters by company depends on this
 * interface rather than reading {@link CompanyProperties} directly, so that when real
 * per-request resolution exists (an {@code api_key} table mapping a presented key to
 * its company), swapping the implementation is the only change needed — no repository
 * call site changes.
 */
public interface CurrentCompany {

    long id();
}
