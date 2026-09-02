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

    InviteUserResponse invite(InviteUserRequest r);

    List<UserRow> list();

    UserRow get(long id);

    User requireUser(long id);

    UserRow changeRole(long id, UpdateUserRoleRequest r);

    UserRow setStatus(long id, UserStatus status);

    Map<Long, String> namesByIds(Collection<Long> ids);
}
