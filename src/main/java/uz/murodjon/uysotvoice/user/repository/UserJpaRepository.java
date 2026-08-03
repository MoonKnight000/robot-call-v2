package uz.murodjon.uysotvoice.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.user.entity.UserEntity;
import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.enums.UserStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link UserEntity}. */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<UserEntity> findByInviteTokenHash(String inviteTokenHash);

    Optional<UserEntity> findByResetTokenHash(String resetTokenHash);

    List<UserEntity> findByCompanyIdOrderById(long companyId);

    boolean existsByCompanyId(long companyId);

    Optional<UserEntity> findByIdAndCompanyId(long id, long companyId);

    /** Cheap id→name lookup for other features to enrich rows with e.g. {@code createdByName}. */
    @Query("SELECT u.id, u.name FROM UserEntity u WHERE u.id IN :ids AND u.companyId = :companyId")
    List<Object[]> findNamesByIds(@Param("ids") Collection<Long> ids, @Param("companyId") long companyId);

    /** §15 "Bugun" mini-statistika — resolve the answering PJSIP endpoint back to a user. */
    Optional<UserEntity> findBySipExtensionAndCompanyId(String sipExtension, long companyId);

    long countByCompanyIdAndRoleAndStatus(long companyId, UserRole role, UserStatus status);

    @Modifying @Transactional
    @Query("UPDATE UserEntity u SET u.lastLoginAt = :at WHERE u.id = :id")
    void touchLastLogin(@Param("id") long id, @Param("at") Instant at);
}
