package uz.murodjon.robotcallv2.profile.application.port.input;

import uz.murodjon.robotcallv2.profile.application.dto.UpdateScheduleRequest;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;

import java.util.List;

public interface ProfileScheduleUseCase {

    List<ScheduleSlot> find();

    /** One user's working hours — what the operator queue reads to know who is on shift. */
    List<ScheduleSlot> findByUserId(long userId);

    List<ScheduleSlot> update(long companyId, UpdateScheduleRequest request);
}
