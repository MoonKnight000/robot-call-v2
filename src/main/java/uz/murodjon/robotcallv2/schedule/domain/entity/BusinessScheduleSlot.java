package uz.murodjon.robotcallv2.schedule.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One open interval on one weekday. A day may carry several (a lunch break is two
 * slots); an interval whose end is before its start runs past midnight into the next day
 * (22:00–06:00).
 */
public record BusinessScheduleSlot(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime startTime,
        @NotNull @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime endTime
) {

    /** Whether this interval continues past midnight. */
    public boolean overnight() {
        return endTime.isBefore(startTime);
    }
}
