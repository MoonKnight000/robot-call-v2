package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Whether a campaign may be dialled right now — by the AI dialer or for an operator.
 *
 * <p>Read in the company's own time zone: a window of 09:00–20:00 means the callee's
 * morning and evening, not the server's, and a tenant in another zone is otherwise
 * dialled at night.
 */
public final class DialWindow {

    private DialWindow() {
    }

    /** @param config the company's settings, or null when it has none */
    public static boolean isOpen(Campaign campaign, CompanyConfig config, Clock clock) {
        Clock local = config != null ? clock.withZone(config.zoneId()) : clock;
        if (config != null && config.isHoliday(LocalDate.now(local))) {
            return false; // a company holiday (NEXT-PLAN C.4)
        }
        Set<DayOfWeek> days = campaign.allowedDays();
        if (days != null && !days.isEmpty() && !days.contains(LocalDate.now(local).getDayOfWeek())) {
            return false;
        }
        LocalTime now = LocalTime.now(local);
        if (!withinRange(now, campaign.dialWindowStart(), campaign.dialWindowEnd())) {
            return false;
        }
        return config == null || withinRange(now, config.dialWindowStart(), config.dialWindowEnd());
    }

    private static boolean withinRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return true;
        }
        return !now.isBefore(start) && now.isBefore(end);
    }
}
