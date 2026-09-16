package uz.murodjon.robotcallv2.security;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Whether the caller may act on a company named in the request — its own, or any for
 * platform staff. Lives next to {@link CurrentUser} because it is a question about the
 * caller, not about a company: both the company and the storage features ask it, and
 * neither may depend on the other for it.
 */
@Component
public class CompanyAccessGuard {

    private final CurrentUser currentUser;

    public CompanyAccessGuard(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Refuses an endpoint that names a company other than the caller's own. Answering 404
     * rather than 403 keeps the existence of another tenant's row from leaking.
     */
    public void requireOwnOrSuperadmin(long callerCompanyId, long companyId) {
        if (currentUser.hasPermission(Permission.PLATFORM_ADMIN)) {
            return;
        }
        if (companyId != callerCompanyId) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId);
        }
    }
}
