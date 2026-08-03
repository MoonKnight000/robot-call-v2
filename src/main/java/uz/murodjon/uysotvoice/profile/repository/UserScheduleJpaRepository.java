package uz.murodjon.uysotvoice.profile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.profile.entity.UserScheduleSlotEntity;

import java.util.List;

/** Spring Data repository for {@link UserScheduleSlotEntity}. */
public interface UserScheduleJpaRepository extends JpaRepository<UserScheduleSlotEntity, Long> {

    List<UserScheduleSlotEntity> findByUserIdOrderByDayOfWeekAscStartTimeAsc(long userId);

    void deleteByUserId(long userId);
}
