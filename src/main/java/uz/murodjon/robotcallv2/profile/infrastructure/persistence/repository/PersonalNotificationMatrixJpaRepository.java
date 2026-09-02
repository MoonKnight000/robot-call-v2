package uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.PersonalNotificationMatrixEntity;

import java.util.List;

@Repository
public interface PersonalNotificationMatrixJpaRepository extends JpaRepository<PersonalNotificationMatrixEntity, Long> {

    @Query("SELECT m FROM PersonalNotificationMatrixEntity m WHERE m.user.id = :userId")
    List<PersonalNotificationMatrixEntity> findByUserId(@Param("userId") long userId);

    @Modifying
    @Query("DELETE FROM PersonalNotificationMatrixEntity m WHERE m.user.id = :userId")
    void deleteByUserId(@Param("userId") long userId);
}
