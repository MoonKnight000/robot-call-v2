package uz.murodjon.robotcallv2.profile.application.port.output;

import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;

import java.util.List;

public interface UserScheduleRepository {

    List<ScheduleSlot> find(long userId);

    List<ScheduleSlot> save(long companyId, long userId, List<ScheduleSlot> slots);
}
