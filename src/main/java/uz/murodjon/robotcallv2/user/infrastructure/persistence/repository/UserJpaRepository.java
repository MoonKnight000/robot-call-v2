package uz.murodjon.robotcallv2.user.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    @Query("SELECT u FROM UserEntity u WHERE u.id = :id AND u.company.id = :companyId")
    Optional<UserEntity> findByIdAndCompanyId(@Param("id") long id, @Param("companyId") long companyId);

    @Query("SELECT u FROM UserEntity u WHERE u.company.id = :companyId ORDER BY u.id")
    List<UserEntity> findByCompanyIdOrderById(@Param("companyId") long companyId);

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByPasswordHash(String passwordHash);

    @Query("SELECT COUNT(u) > 0 FROM UserEntity u WHERE u.company.id = :companyId")
    boolean existsByCompanyId(@Param("companyId") long companyId);

    @Query("SELECT COUNT(u) FROM UserEntity u WHERE u.company.id = :companyId "
            + "AND u.role.id IN :roleIds AND u.status = :status")
    long countByCompanyIdAndRoleIdsAndStatus(@Param("companyId") long companyId,
                                             @Param("roleIds") Collection<Long> roleIds,
                                             @Param("status") UserStatus status);

    Optional<UserEntity> findByInviteTokenHash(String inviteTokenHash);

    Optional<UserEntity> findByResetTokenHash(String resetTokenHash);

    @Query("SELECT u FROM UserEntity u WHERE u.sipExtension = :sipExtension AND u.company.id = :companyId")
    Optional<UserEntity> findBySipExtensionAndCompanyId(@Param("sipExtension") String sipExtension,
                                                        @Param("companyId") long companyId);

    @Query("SELECT u.id, u.name FROM UserEntity u WHERE u.id IN :ids AND u.company.id = :companyId")
    List<Object[]> findNamesByIds(@Param("ids") Collection<Long> ids, @Param("companyId") long companyId);

    /** Rows of (role id, user count) for the company's users. */
    @Query("SELECT u.role.id, COUNT(u) FROM UserEntity u WHERE u.company.id = :companyId GROUP BY u.role.id")
    List<Object[]> countByCompanyIdGroupedByRoleId(@Param("companyId") long companyId);

    @Query("SELECT u.id FROM UserEntity u WHERE u.company.id = :companyId AND u.role.id = :roleId")
    List<Long> findIdsByCompanyIdAndRoleId(@Param("companyId") long companyId, @Param("roleId") long roleId);

    @Modifying
    @Query("UPDATE UserEntity u SET u.lastLoginAt = :now WHERE u.id = :id")
    void touchLastLogin(@Param("id") long id, @Param("now") Instant now);
}
