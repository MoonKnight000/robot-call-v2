package uz.murodjon.robotcallv2.callrecord.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What a company's calls to one phone number add up to, as far as its contact policy
 * cares ({@code CompanyConfig#limitsContactFrequency}).
 *
 * @param recentAttempts     start times of the placed outbound attempts in the last seven
 *                           days, newest first — never more than the cap asked for
 * @param lastConversationAt when someone last actually talked with this number (either
 *                           direction), or null; a callback the person asked for does not count
 * @param promisedUntil      the latest date this number promised to pay by, or null
 */
public record ContactHistory(List<Instant> recentAttempts, Instant lastConversationAt, LocalDate promisedUntil) {

    public static final ContactHistory NONE = new ContactHistory(List.of(), null, null);

    public ContactHistory {
        recentAttempts = recentAttempts == null ? List.of() : List.copyOf(recentAttempts);
    }
}
