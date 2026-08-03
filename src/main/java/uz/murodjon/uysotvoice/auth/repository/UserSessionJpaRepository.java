package uz.murodjon.uysotvoice.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.auth.entity.UserSessionEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link UserSessionEntity}. */
@Repository
public interface UserSessionJpaRepository extends JpaRepository<UserSessionEntity, Long> {

    Optional<UserSessionEntity> findByRefreshTokenHashAndRevokedAtIsNull(String refreshTokenHash);

    List<UserSessionEntity> findByUserIdAndRevokedAtIsNullOrderByLastActivityAtDesc(long userId);

    Optional<UserSessionEntity> findByIdAndUserId(long id, long userId);
}
