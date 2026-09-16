package uz.murodjon.robotcallv2.schedule.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessSchedule;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleSlot;
import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.BusinessScheduleEntity;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.BusinessScheduleSlotEntity;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.HolidayEntity;

import java.util.List;

@Component
public class BusinessScheduleEntityMapper {

    public BusinessSchedule toBusinessSchedule(BusinessScheduleEntity entity) {
        if (entity == null) {
            return null;
        }
        List<BusinessScheduleSlot> slots = entity.getSlots().stream()
                .map(slot -> new BusinessScheduleSlot(slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                .toList();
        return new BusinessSchedule(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getTimezone(),
                slots, entity.getCreatedAt());
    }

    public List<BusinessScheduleSlotEntity> toSlotEntities(List<BusinessScheduleSlot> slots) {
        return slots.stream()
                .map(slot -> new BusinessScheduleSlotEntity(slot.dayOfWeek(), slot.startTime(), slot.endTime()))
                .toList();
    }

    public Holiday toHoliday(HolidayEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Holiday(entity.getId(), entity.getCompanyId(), entity.getDate(), entity.getName(), entity.getKind(),
                entity.getOpenTime(), entity.getCloseTime(), entity.getCreatedAt());
    }
}
