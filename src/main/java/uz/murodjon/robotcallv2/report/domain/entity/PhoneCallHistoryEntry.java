package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

/**
 * One call to a number, newest first — what the contact drawer's call-history timeline
 * (§10.8) is built from. The report port's own type, so the port never hands out another
 * feature's HTTP DTO.
 */
public record PhoneCallHistoryEntry(
        long callId,
        String campaignName,
        Instant startedAt,
        Integer durationSec,
        String disposition
) {
}
