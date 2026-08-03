package uz.murodjon.uysotvoice.profile.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.profile.dto.ScheduleSlot;
import uz.murodjon.uysotvoice.profile.entity.UserScheduleSlotEntity;

import java.time.Instant;
import java.util.List;

/** JPA-backed DAO for {@code user_schedule_slot} (API-REQUIREMENTS §15). */
@Repository
public class UserScheduleRepository {

    private final UserScheduleJpaRepository jpa;
    private final CurrentCompany company;

    public UserScheduleRepository(UserScheduleJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public List<ScheduleSlot> find(long userId) {
        return jpa.findByUserIdOrderByDayOfWeekAscStartTimeAsc(userId).stream()
                .map(e -> new ScheduleSlot(e.getDayOfWeek(), e.getStartTime(), e.getEndTime()))
                .toList();
    }

    /** Whole-grid replace — same reasoning as the notification matrices. */
    @Transactional
    public List<ScheduleSlot> save(long userId, List<ScheduleSlot> slots) {
        jpa.deleteByUserId(userId);
        long companyId = company.id();
        Instant now = Instant.now();
        for (ScheduleSlot slot : slots) {
            UserScheduleSlotEntity entity = new UserScheduleSlotEntity();
            entity.setCompanyId(companyId);
            entity.setUserId(userId);
            entity.setDayOfWeek(slot.dayOfWeek());
            entity.setStartTime(slot.startTime());
            entity.setEndTime(slot.endTime());
            entity.setCreatedAt(now);
            jpa.save(entity);
        }
        return find(userId);
    }
}
