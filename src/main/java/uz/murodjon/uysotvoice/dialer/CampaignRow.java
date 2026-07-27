package uz.murodjon.uysotvoice.dialer;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * A row of {@code campaign} (PROJECT.md §6).
 *
 * @param dialDays comma-separated {@link DayOfWeek} names this campaign may dial on
 *                 (§11.2); blank means every day
 * @param ttsVoice catalog id of the voice this campaign speaks with (§2.5); null keeps
 *                 the configured provider/voice routing
 * @param dailyCallCap most calls this campaign may dial in one day; 0 = unlimited. A cost
 *                 control, not a rate limit — {@code dispatch_batch} and
 *                 {@code max_concurrent_calls} shape the pace, this bounds the total
 */
public record CampaignRow(
        long id,
        String name,
        String type,
        String status,
        String goalPrompt,
        String defaultLanguage,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        String dialDays,
        int maxAttempts,
        int retryIntervalHours,
        int maxConcurrentCalls,
        String ttsVoice,
        int dailyCallCap
) {

    /**
     * Weekdays this campaign may dial on. An unparsable or empty value allows every
     * day — a typo in configuration must not silently stop a campaign, and the dial
     * window still applies.
     */
    public Set<DayOfWeek> allowedDays() {
        if (dialDays == null || dialDays.isBlank()) {
            return EnumSet.allOf(DayOfWeek.class);
        }
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(dialDays.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .forEach(part -> {
                    try {
                        days.add(DayOfWeek.valueOf(part.toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                        // unknown day name — skip it
                    }
                });
        return days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : days;
    }
}
