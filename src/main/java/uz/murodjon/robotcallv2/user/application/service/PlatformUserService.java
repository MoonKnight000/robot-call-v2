package uz.murodjon.robotcallv2.user.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.role.application.port.input.RoleUseCase;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.application.port.input.PlatformUserUseCase;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.util.List;

/**
 * The same user operations a company's own admin has, aimed at a company chosen by the
 * platform. {@link UserService} already takes the company as an argument and lets a
 * {@code PLATFORM_ADMIN} caller hand out DEVELOPER/SUPERADMIN, so this only adds the one
 * thing it cannot know: that the named company exists.
 */
@Service
public class PlatformUserService implements PlatformUserUseCase {

    private final CompanyUseCase companyUseCase;
    private final UserUseCase userUseCase;
    private final RoleUseCase roleUseCase;

    public PlatformUserService(CompanyUseCase companyUseCase, UserUseCase userUseCase, RoleUseCase roleUseCase) {
        this.companyUseCase = companyUseCase;
        this.userUseCase = userUseCase;
        this.roleUseCase = roleUseCase;
    }

    @Override
    public List<UserRow> list(long companyId) {
        companyUseCase.requireExists(companyId);
        return userUseCase.list(companyId);
    }

    @Override
    public UserRow requireUserRow(long companyId, long id) {
        companyUseCase.requireExists(companyId);
        return userUseCase.requireUserRow(companyId, id);
    }

    @Override
    public List<RoleRow> findRoles(long companyId) {
        companyUseCase.requireExists(companyId);
        return roleUseCase.findByCompanyId(companyId);
    }

    @Override
    public InviteUserResponse invite(long companyId, InviteUserRequest request) {
        companyUseCase.requireExists(companyId);
        return userUseCase.invite(companyId, request);
    }

    @Override
    public UserRow changeRole(long companyId, long id, UpdateUserRoleRequest request) {
        companyUseCase.requireExists(companyId);
        return userUseCase.changeRole(companyId, id, request);
    }

    @Override
    public UserRow block(long companyId, long id) {
        companyUseCase.requireExists(companyId);
        return userUseCase.setStatus(companyId, id, UserStatus.BLOCKED);
    }

    @Override
    public UserRow unblock(long companyId, long id) {
        companyUseCase.requireExists(companyId);
        return userUseCase.setStatus(companyId, id, UserStatus.ACTIVE);
    }
}
