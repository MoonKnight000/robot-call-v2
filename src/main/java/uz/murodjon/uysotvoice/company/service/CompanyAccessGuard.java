package uz.murodjon.uysotvoice.company.service;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

/**
 * Guards the company-identity/config endpoints a tenant's own {@code ADMIN} may call
 * (report #3): self-service, own company only. {@code SUPERADMIN} bypasses — platform
 * staff deciding on a company's status needs to view any tenant, not just one.
 *
 * <p>A mismatch is reported as {@link NotFoundException}, not {@code ForbiddenException}
 * — same "another company's row is a 404, not a 403" convention every other
 * cross-tenant check in this codebase follows (CLAUDE.md exception table), so a company
 * id's mere existence is never revealed to a caller that cannot see it.
 */
@Component
public class CompanyAccessGuard {

    private final CurrentCompany company;
    private final CurrentUser currentUser;

    public CompanyAccessGuard(CurrentCompany company, CurrentUser currentUser) {
        this.company = company;
        this.currentUser = currentUser;
    }

    /** Throws {@link NotFoundException} unless the caller is {@code SUPERADMIN} or {@code companyId} is their own. */
    public void requireOwnOrSuperadmin(long companyId) {
        if (currentUser.role().map(role -> role == UserRole.SUPERADMIN).orElse(false)) {
            return;
        }
        if (companyId != company.id()) {
            throw new NotFoundException("company", companyId);
        }
    }
}
