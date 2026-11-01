package uz.murodjon.robotcallv2.campaign.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.LocalTime;

/**
 * One stretch of a day a campaign may call in: from {@code start} (inclusive) to {@code end} (exclusive).
 */
public record TimeRange(
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime start,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime end
) {

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
