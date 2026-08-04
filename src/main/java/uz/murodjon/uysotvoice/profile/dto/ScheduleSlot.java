package uz.murodjon.uysotvoice.profile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One weekly time window in {@code GET/PUT /api/profile/schedule} (§15 "Ish jadvali" tab). */
public record ScheduleSlot(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime startTime,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime endTime
) {
}
