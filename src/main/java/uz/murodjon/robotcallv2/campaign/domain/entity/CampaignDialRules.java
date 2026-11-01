package uz.murodjon.robotcallv2.campaign.domain.entity;

import uz.murodjon.robotcallv2.campaign.domain.enums.TimezoneMode;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * The calling rules a campaign adds to its plain window: whose clock the window is read on, hours
 * that differ from one weekday to the next, and whether company holidays are skipped.
 *
 * <p>Every part is optional and an empty set of rules changes nothing: the window and days of
 * {@link CampaignSchedule} apply as before.
 *
 * @param timezoneMode    whose clock the window is read on; null keeps how a campaign always behaved
 * @param fixedTimezone   the zone id (for example {@code Asia/Tashkent}) when {@code timezoneMode} is FIXED
 * @param dayWindows      the ranges calls may be placed in on each weekday; a weekday left out is closed.
 *                        Null or empty: the single window and the days of the schedule apply instead
 * @param excludeHolidays whether the company's holidays are skipped; null means yes
 */
public record CampaignDialRules(
        TimezoneMode timezoneMode,
        String fixedTimezone,
        Map<DayOfWeek, List<TimeRange>> dayWindows,
        Boolean excludeHolidays
) {

    /** No extra rules: the campaign dials by its plain window, as before. */
    public static final CampaignDialRules NONE = new CampaignDialRules(null, null, null, null);

    public CampaignDialRules {
        dayWindows = dayWindows == null || dayWindows.isEmpty() ? null : Map.copyOf(dayWindows);
    }

    public boolean hasDayWindows() {
        return dayWindows != null;
    }

    public boolean holidaysExcluded() {
        return excludeHolidays == null || excludeHolidays;
    }
}
