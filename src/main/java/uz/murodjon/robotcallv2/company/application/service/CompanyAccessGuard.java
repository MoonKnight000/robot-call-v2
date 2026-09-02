package uz.murodjon.robotcallv2.company.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.enums.UserRole;

@Component
public class CompanyAccessGuard {

    private final CurrentCompany company;
    private final CurrentUser currentUser;

    public CompanyAccessGuard(CurrentCompany company, CurrentUser currentUser) {
        this.company = company;
        this.currentUser = currentUser;
    }

    public void requireOwnOrSuperadmin(long companyId) {
        if (currentUser.role().map(role -> role == UserRole.SUPERADMIN).orElse(false)) {
            return;
        }
        if (companyId != company.id()) {
            throw new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId);
        }
    }
}
