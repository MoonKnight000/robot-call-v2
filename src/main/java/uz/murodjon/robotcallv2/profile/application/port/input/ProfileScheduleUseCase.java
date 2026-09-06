package uz.murodjon.robotcallv2.profile.application.port.input;

import uz.murodjon.robotcallv2.profile.application.dto.UpdateScheduleRequest;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;

import java.util.List;

public interface ProfileScheduleUseCase {

    List<ScheduleSlot> find();

    List<ScheduleSlot> update(long companyId, UpdateScheduleRequest request);
}
