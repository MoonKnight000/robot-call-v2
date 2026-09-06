package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

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
