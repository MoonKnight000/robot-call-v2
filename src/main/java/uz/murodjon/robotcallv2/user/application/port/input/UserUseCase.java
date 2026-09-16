package uz.murodjon.robotcallv2.user.application.port.input;

import uz.murodjon.robotcallv2.user.application.dto.InviteUserRequest;
import uz.murodjon.robotcallv2.user.application.dto.InviteUserResponse;
import uz.murodjon.robotcallv2.user.application.dto.UpdateUserRoleRequest;
import uz.murodjon.robotcallv2.user.application.dto.UserRow;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserUseCase {

    InviteUserResponse invite(long companyId, InviteUserRequest request);

    List<UserRow> list(long companyId);

    UserRow requireUserRow(long companyId, long id);

    User requireUser(long companyId, long id);

    /** The company's user dialled as {@code extension}, or null when nobody has it. */
    User findBySipExtension(long companyId, String extension);

    UserRow changeRole(long companyId, long id, UpdateUserRoleRequest request);

    UserRow setStatus(long companyId, long id, UserStatus status);

    Map<Long, String> findNamesByIds(long companyId, Collection<Long> ids);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /** Not company-scoped: for flows that start from a token, before the company is known. */
    Optional<User> findById(long id);

    Optional<User> findByInviteTokenHash(String tokenHash);

    Optional<User> findByResetTokenHash(String tokenHash);

    boolean existsByEmail(String email);

    void activate(long id, String passwordHash);

    void setResetToken(long id, String tokenHash, Instant expiresAt);

    void resetPassword(long id, String passwordHash);

    void touchLastLogin(long id);

    void updateProfile(long companyId, long id, String name, String email, String phone, String position,
                       String sipExtension);

    void updateAvatarFileId(long companyId, long id, Long avatarFileId);

    void updatePassword(long companyId, long id, String passwordHash);

    void updateCallColumns(long companyId, long id, String callColumns);
}
