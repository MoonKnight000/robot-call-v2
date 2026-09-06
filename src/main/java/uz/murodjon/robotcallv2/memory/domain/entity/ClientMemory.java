package uz.murodjon.robotcallv2.memory.domain.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What a company knows about one phone number across every call it ever had with it —
 * outbound from any campaign and inbound alike. Keyed by {@code (companyId, phone)}
 * rather than by campaign target, so the knowledge outlives the campaign that
 * produced it and is there when the client calls in.
 *
 * <p>{@code recentCalls} is newest first and capped at {@link #MAX_RECENT_CALLS};
 * {@code facts} carries the scenario outcome fields worth repeating next time
 * ({@code promisedDate}, {@code reasonCode}, ...), newest value winning.
 */
public record ClientMemory(
        long id,
        long companyId,
        String phone,
        String preferredName,
        String preferredLanguage,
        String operatorNotes,
        List<RememberedCall> recentCalls,
        Map<String, Object> facts,
        Instant updatedAt
) {

    public static final int MAX_RECENT_CALLS = 3;

    public ClientMemory {
        recentCalls = recentCalls == null ? List.of() : List.copyOf(recentCalls);
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    /** An empty memory for a phone nobody has talked to yet. */
    public static ClientMemory empty(long companyId, String phone) {
        return new ClientMemory(0, companyId, phone, null, null, null, List.of(), Map.of(), null);
    }

    public boolean isEmpty() {
        return preferredName == null && operatorNotes == null && recentCalls.isEmpty() && facts.isEmpty();
    }
}
