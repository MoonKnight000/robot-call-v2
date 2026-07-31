package uz.murodjon.uysotvoice.callrecord.dto;

import uz.murodjon.uysotvoice.agent.dialog.CallSummary;

/** A written result whose CRM note has not been accepted yet. */
public record PendingNote(long callId, long clientId, CallSummary summary) {
}
