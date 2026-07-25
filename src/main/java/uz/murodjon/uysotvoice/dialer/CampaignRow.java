package uz.murodjon.uysotvoice.dialer;

import java.time.LocalTime;

/** A row of {@code campaign} (PROJECT.md §6). */
public record CampaignRow(
        long id,
        String name,
        String type,
        String status,
        String goalPrompt,
        String defaultLanguage,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        int maxAttempts,
        int retryIntervalHours,
        int maxConcurrentCalls
) {
}
