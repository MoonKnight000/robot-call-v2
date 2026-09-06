package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

@Component
public class CompanyAccessGuard {

    private final CurrentCompany company;
    private final CurrentUser currentUser;

    public CompanyAccessGuard(CurrentCompany company, CurrentUser currentUser) {
        this.company = company;
        this.currentUser = currentUser;
    }

    public void requireOwnOrSuperadmin(long companyId) {
        if (currentUser.hasPermission(Permission.PLATFORM_ADMIN)) {
            return;
        }
        if (companyId != company.id()) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId);
        }
    }
}
