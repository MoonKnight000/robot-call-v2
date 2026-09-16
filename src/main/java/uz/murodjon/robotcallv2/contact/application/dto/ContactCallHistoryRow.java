package uz.murodjon.robotcallv2.contact.application.dto;

import java.time.Instant;

/**
 * One line of a contact's call-history timeline (§10.8 drawer).
 */
public record ContactCallHistoryRow(
        long callId,
        String campaignName,
        Instant startedAt,
        Integer durationSec,
        String disposition
) {
}
