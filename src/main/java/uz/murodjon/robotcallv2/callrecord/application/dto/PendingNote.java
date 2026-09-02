package uz.murodjon.robotcallv2.callrecord.application.dto;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;

public record PendingNote(
        long callId,
        long clientId,
        CallSummary summary
) {
}
