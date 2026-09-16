package uz.murodjon.robotcallv2.schedule.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleSlot;

import java.util.List;

/**
 * Body of POST/PUT /api/schedules.
 *
 * @param timezone IANA zone ("Asia/Tashkent"); null or blank uses the company's
 */
public record SaveBusinessScheduleRequest(
        @NotBlank String name,
        String timezone,
        @NotEmpty List<@Valid BusinessScheduleSlot> slots
) {
}
