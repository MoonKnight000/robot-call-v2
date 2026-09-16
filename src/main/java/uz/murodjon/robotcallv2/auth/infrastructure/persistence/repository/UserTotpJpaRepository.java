package uz.murodjon.robotcallv2.auth.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity.UserTotpEntity;

public interface UserTotpJpaRepository extends JpaRepository<UserTotpEntity, Long> {

    /** One statement, so two logins racing with the same code cannot both claim its step. */
    @Modifying
    @Transactional
    @Query("UPDATE UserTotpEntity t SET t.lastUsedStep = :step WHERE t.userId = :userId AND t.lastUsedStep < :step")
    int markUsed(@Param("userId") long userId, @Param("step") long step);
}
