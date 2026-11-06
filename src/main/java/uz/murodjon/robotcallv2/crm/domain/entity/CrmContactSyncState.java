package uz.murodjon.robotcallv2.crm.domain.entity;

import uz.murodjon.robotcallv2.crm.domain.enums.CrmContactSyncStatus;

import java.time.Instant;

/**
 * How far one company's CRM contact sync has got.
 *
 * @param provider   the CRM the cursor was read from; another one means a full sync
 * @param cursor     the newest modification time imported so far; {@code null} — full sync next
 * @param resume     where an unfinished pass stopped; {@code null} — none open, the next pass reads from {@code cursor}
 * @param nextRunAt  when the scheduler runs the sync next; {@code null} — not scheduled
 * @param lastRunAt  when the last run ended; {@code null} — never ran
 * @param lastStatus how it ended; {@code null} — never ran
 * @param lastError  why it failed, {@code null} otherwise
 * @param lastCounts what the last run did
 */
public record CrmContactSyncState(
        String provider,
        Instant cursor,
        CrmContactSyncResume resume,
        Instant nextRunAt,
        Instant lastRunAt,
        CrmContactSyncStatus lastStatus,
        String lastError,
        CrmContactSyncCounts lastCounts
) {

    public CrmContactSyncState {
        lastCounts = lastCounts == null ? CrmContactSyncCounts.NONE : lastCounts;
    }

    /** A company that never ran a sync. */
    public static CrmContactSyncState empty() {
        return new CrmContactSyncState(null, null, null, null, null, null, null, CrmContactSyncCounts.NONE);
    }
}
