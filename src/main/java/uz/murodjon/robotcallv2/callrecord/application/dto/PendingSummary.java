package uz.murodjon.robotcallv2.callrecord.application.dto;

public record PendingSummary(
        long callId,
        long targetId,
        long companyId
) {
}
