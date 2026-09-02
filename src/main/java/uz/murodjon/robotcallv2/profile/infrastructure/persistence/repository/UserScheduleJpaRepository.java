package uz.murodjon.robotcallv2.profile.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.profile.infrastructure.persistence.entity.UserScheduleSlotEntity;

import java.util.List;

@Repository
public interface UserScheduleJpaRepository extends JpaRepository<UserScheduleSlotEntity, Long> {

    @Query("SELECT s FROM UserScheduleSlotEntity s WHERE s.user.id = :userId ORDER BY s.dayOfWeek ASC, s.startTime ASC")
    List<UserScheduleSlotEntity> findByUserIdOrderByDayOfWeekAscStartTimeAsc(@Param("userId") long userId);

    @Modifying
    @Query("DELETE FROM UserScheduleSlotEntity s WHERE s.user.id = :userId")
    void deleteByUserId(@Param("userId") long userId);
}
