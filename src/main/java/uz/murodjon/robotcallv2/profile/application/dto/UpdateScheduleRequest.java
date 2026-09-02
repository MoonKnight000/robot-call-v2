package uz.murodjon.robotcallv2.profile.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.profile.domain.entity.ScheduleSlot;

import java.util.List;

public record UpdateScheduleRequest(
        @NotNull List<@Valid ScheduleSlot> slots
) {
}
