package uz.murodjon.robotcallv2.user.application.port.input;

import uz.murodjon.robotcallv2.role.application.dto.RoleRow;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;

import java.util.List;

/** Platform staff managing the users of any company — the company id comes from the URL, not from the token. */
public interface PlatformUserUseCase {

    List<UserRow> list(long companyId);

    UserRow requireUserRow(long companyId, long id);

    List<RoleRow> findRoles(long companyId);

    InviteUserResponse invite(long companyId, InviteUserRequest request);

    UserRow changeRole(long companyId, long id, UpdateUserRoleRequest request);

    UserRow block(long companyId, long id);

    UserRow unblock(long companyId, long id);
}
