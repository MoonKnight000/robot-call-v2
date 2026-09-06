package uz.murodjon.robotcallv2.user.application.port.output;

import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserRepository {

    long create(String name, String username, String email, long roleId, UserStatus status);

    long createForCompany(long companyId, String name, String username, String email, String passwordHash,
                          long roleId, UserStatus status);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findById(long id);

    User find(long id);

    User findBySipExtension(String sipExtension, long companyId);

    List<User> findAll();

    Map<Long, String> namesByIds(Collection<Long> ids);

    List<User> findActiveByCompany(long companyId);

    boolean hasAnyUser(long companyId);

    /** Active users holding any of these roles — how "the last admin" is counted now. */
    long countActiveByRoleIds(long companyId, Collection<Long> roleIds);

    void setInviteToken(long id, String tokenHash, Instant expiresAt);

    Optional<User> findByInviteTokenHash(String tokenHash);

    void activate(long id, String passwordHash);

    void setResetToken(long id, String tokenHash, Instant expiresAt);

    Optional<User> findByResetTokenHash(String tokenHash);

    void resetPassword(long id, String passwordHash);

    void updateRole(long id, long roleId);

    void updateStatus(long id, UserStatus status);

    void touchLastLogin(long id);

    void updateProfile(long id, String name, String email, String phone, String position, String sipExtension);

    void updateAvatarFileId(long id, Long avatarFileId);

    void updatePassword(long id, String passwordHash);

    void updateCallColumns(long id, String callColumns);
}
