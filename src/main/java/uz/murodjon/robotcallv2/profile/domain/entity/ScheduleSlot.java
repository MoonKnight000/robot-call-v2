package uz.murodjon.robotcallv2.profile.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record ScheduleSlot(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime startTime,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime endTime
) {
}
