package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** {@code PUT /api/profile/schedule} body — whole-grid replace, same shape as the notification matrix. */
public record UpdateScheduleRequest(
        @NotNull @Valid List<ScheduleSlot> slots
) {
}
