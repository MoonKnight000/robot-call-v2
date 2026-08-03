package uz.murodjon.uysotvoice.callrecord.dto;

/** A finished call with a transcript but no result row — the summary failed. */
public record PendingSummary(long callId, long clientId, long scenarioId) {
}
