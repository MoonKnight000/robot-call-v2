package uz.murodjon.uysotvoice.profile.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One weekly time window in {@code GET/PUT /api/profile/schedule} (§15 "Ish jadvali" tab). */
public record ScheduleSlot(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime
) {
}
