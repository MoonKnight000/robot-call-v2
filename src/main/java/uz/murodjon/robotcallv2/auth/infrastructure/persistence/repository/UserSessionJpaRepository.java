package uz.murodjon.robotcallv2.auth.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity.UserSessionEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, Long> {

    Optional<UserSessionEntity> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    @Query("SELECT s FROM UserSessionEntity s WHERE s.id = :id AND s.user.id = :userId")
    Optional<UserSessionEntity> findByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

    @Query("SELECT s FROM UserSessionEntity s WHERE s.user.id = :userId AND s.revokedAt IS NULL ORDER BY s.lastActivityAt DESC")
    List<UserSessionEntity> findByUserIdAndRevokedAtIsNullOrderByLastActivityAtDesc(@Param("userId") long userId);
}
