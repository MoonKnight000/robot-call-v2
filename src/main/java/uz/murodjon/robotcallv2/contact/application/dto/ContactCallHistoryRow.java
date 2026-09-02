package uz.murodjon.robotcallv2.contact.application.dto;

import uz.murodjon.robotcallv2.report.domain.entity.CallRow;

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
    public static ContactCallHistoryRow fromCallRow(CallRow row) {
        return new ContactCallHistoryRow(
                row.callId(),
                row.campaignName(),
                row.startedAt(),
                row.durationSec(),
                row.disposition() != null ? row.disposition().name() : null
        );
    }
}

