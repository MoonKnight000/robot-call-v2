package uz.murodjon.robotcallv2.user.application.port.input;

import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserUseCase {

    InviteUserResponse invite(long companyId, InviteUserRequest request);

    List<UserRow> list(long companyId);

    UserRow get(long companyId, long id);

    User requireUser(long companyId, long id);

    UserRow changeRole(long companyId, long id, UpdateUserRoleRequest request);

    UserRow setStatus(long companyId, long id, UserStatus status);

    Map<Long, String> namesByIds(long companyId, Collection<Long> ids);
}
