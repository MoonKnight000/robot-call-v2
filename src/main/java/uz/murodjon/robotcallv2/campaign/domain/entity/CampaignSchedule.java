package uz.murodjon.robotcallv2.campaign.domain.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * When a campaign may call: the period it runs in, and the hours and days inside it.
 *
 * @param startAt         when a started campaign goes ACTIVE; in the future it waits as SCHEDULED, null means now
 * @param endAt           when an ACTIVE campaign is closed as COMPLETED whatever targets remain; null means never
 * @param dialWindowStart first moment of the day a client may be called
 * @param dialWindowEnd   last moment of the day a client may be called
 * @param dialDays        days the campaign may dial on; empty or null means every day (§11.2)
 */
public record CampaignSchedule(
        Instant startAt,
        Instant endAt,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays
) {

    public Set<DayOfWeek> allowedDays() {
        return dialDays == null || dialDays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : dialDays;
    }
}
