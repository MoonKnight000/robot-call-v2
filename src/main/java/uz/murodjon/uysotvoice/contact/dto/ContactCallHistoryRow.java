package uz.murodjon.uysotvoice.contact.dto;

import java.time.Instant;

/**
 * One line of a contact's call-history timeline (§10.8 drawer) — a thin projection,
 * not a duplicate of {@code report.CallRow}, since the drawer only ever shows a
 * summary line per call.
 */
public record ContactCallHistoryRow(
        long callId,
        String campaignName,
        Instant startedAt,
        Integer durationSec,
        String disposition
) {
}
