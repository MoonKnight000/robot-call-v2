package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;

/**
 * A finished call the outbox still has to summarize: the CRM client it was about, the
 * scenario the live call ran (for its outcome schema) and how it ended.
 */
public record PendingSummary(
        long callId,
        long clientId,
        long scenarioId,
        Disposition disposition
) {
}
