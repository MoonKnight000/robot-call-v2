package uz.murodjon.robotcallv2.campaign.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignSchedule;
import uz.murodjon.robotcallv2.shared.util.DateTimeProperties;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

/**
 * {@link CampaignSchedule} as the campaign endpoints read and write it.
 *
 * @param dialWindowStart omitted: on create the company's own dial window, on edit the campaign's
 * @param dialDays        omit or leave empty for every day of the week (§11.2)
 */
public record CampaignScheduleDto(
        Instant startAt,
        Instant endAt,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowStart,
        @JsonFormat(pattern = DateTimeProperties.TIME_PATTERN) LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays
) {

    public static CampaignScheduleDto of(CampaignSchedule schedule) {
        return new CampaignScheduleDto(schedule.startAt(), schedule.endAt(), schedule.dialWindowStart(),
                schedule.dialWindowEnd(), schedule.dialDays());
    }
}
