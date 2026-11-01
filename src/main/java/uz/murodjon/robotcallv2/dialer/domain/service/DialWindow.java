package uz.murodjon.robotcallv2.dialer.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignDialRules;
import uz.murodjon.robotcallv2.campaign.domain.entity.TimeRange;
import uz.murodjon.robotcallv2.campaign.domain.enums.TimezoneMode;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
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
        return isOpen(campaign, config, Set.of(), clock);
    }

    /**
     * @param config   the company's settings, or null when it has none
     * @param holidays dates the company does not dial on
     */
    public static boolean isOpen(Campaign campaign, CompanyConfig config, Set<LocalDate> holidays, Clock clock) {
        if (hasOwnRules(campaign)) {
            return isOpenByRules(campaign, config, holidays, null, clock);
        }
        Clock local = config != null ? clock.withZone(config.zoneId()) : clock;
        if (holidays != null && holidays.contains(LocalDate.now(local))) {
            return false; // a company holiday (NEXT-PLAN C.4)
        }
        Set<DayOfWeek> days = campaign.schedule().allowedDays();
        if (days != null && !days.isEmpty() && !days.contains(LocalDate.now(local).getDayOfWeek())) {
            return false;
        }
        LocalTime now = LocalTime.now(local);
        if (!withinRange(now, campaign.schedule().dialWindowStart(), campaign.schedule().dialWindowEnd())) {
            return false;
        }
        return config == null || withinRange(now, config.dialWindowStart(), config.dialWindowEnd());
    }

    /**
     * Evaluates whether calling {@code recipientPhone} is allowed right now:
     * (1) the company's own dial window and holidays must be open, AND
     * (2) the recipient's local time (in their regional timezone) must be within daytime hours (08:00 - 20:00).
     */
    public static boolean isOpenForRecipient(Campaign campaign, CompanyConfig config, String recipientPhone, Clock clock) {
        return isOpenForRecipient(campaign, config, Set.of(), recipientPhone, clock);
    }

    public static boolean isOpenForRecipient(Campaign campaign, CompanyConfig config, Set<LocalDate> holidays,
                                             String recipientPhone, Clock clock) {
        if (hasOwnRules(campaign)) {
            return isOpenByRules(campaign, config, holidays, recipientPhone, clock);
        }
        if (!isOpen(campaign, config, holidays, clock)) {
            return false;
        }
        java.time.ZoneId companyZone = config != null ? config.zoneId() : clock.getZone();
        java.time.ZoneId recipientZone = RecipientTimezoneResolver.resolve(recipientPhone, companyZone);
        if (recipientZone.equals(companyZone)) {
            return true;
        }
        LocalTime recipientLocalTime = LocalTime.now(clock.withZone(recipientZone));
        LocalTime start = campaign.schedule().dialWindowStart() != null ? campaign.schedule().dialWindowStart() : LocalTime.of(8, 0);
        LocalTime end = campaign.schedule().dialWindowEnd() != null ? campaign.schedule().dialWindowEnd() : LocalTime.of(20, 0);
        return withinRange(recipientLocalTime, start, end);
    }

    /** Whether the campaign set rules of its own; without them the window behaves as it always has. */
    private static boolean hasOwnRules(Campaign campaign) {
        CampaignDialRules rules = campaign.schedule().dialRules();
        return rules.timezoneMode() != null || rules.hasDayWindows() || !rules.holidaysExcluded();
    }

    /**
     * The window read by the campaign's own rules. Unlike the plain window there is no extra
     * quiet-hours check on the person called: when the window is to follow them
     * ({@link TimezoneMode#CONTACT}) it is read on their clock, otherwise on the one the campaign names.
     * The company's own hours still bind, on the company's clock.
     *
     * @param recipientPhone the number about to be called, or null when asking about the campaign as a whole
     */
    private static boolean isOpenByRules(Campaign campaign, CompanyConfig config, Set<LocalDate> holidays,
                                         String recipientPhone, Clock clock) {
        CampaignDialRules rules = campaign.schedule().dialRules();
        ZoneId companyZone = config != null ? config.zoneId() : clock.getZone();
        Clock companyClock = clock.withZone(companyZone);
        if (rules.holidaysExcluded() && holidays != null && holidays.contains(LocalDate.now(companyClock))) {
            return false;
        }
        Clock local = clock.withZone(zoneOf(rules, companyZone, recipientPhone));
        LocalDate today = LocalDate.now(local);
        LocalTime now = LocalTime.now(local);
        boolean open;
        if (rules.hasDayWindows()) {
            List<TimeRange> ranges = rules.dayWindows().get(today.getDayOfWeek());
            open = ranges != null && ranges.stream().anyMatch(range -> range.contains(now));
        } else {
            Set<DayOfWeek> days = campaign.schedule().allowedDays();
            open = (days == null || days.isEmpty() || days.contains(today.getDayOfWeek()))
                    && withinRange(now, campaign.schedule().dialWindowStart(), campaign.schedule().dialWindowEnd());
        }
        if (!open) {
            return false;
        }
        return config == null || withinRange(LocalTime.now(companyClock), config.dialWindowStart(), config.dialWindowEnd());
    }

    private static ZoneId zoneOf(CampaignDialRules rules, ZoneId companyZone, String recipientPhone) {
        TimezoneMode mode = rules.timezoneMode();
        if (mode == TimezoneMode.FIXED) {
            try {
                return ZoneId.of(rules.fixedTimezone());
            } catch (RuntimeException e) {
                return companyZone; // validated on save; a stored value that no longer resolves must not stop the dialer
            }
        }
        if (mode == TimezoneMode.CONTACT && recipientPhone != null) {
            return RecipientTimezoneResolver.resolve(recipientPhone, companyZone);
        }
        return companyZone;
    }

    private static boolean withinRange(LocalTime now, LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return true;
        }
        return !now.isBefore(start) && now.isBefore(end);
    }
}
