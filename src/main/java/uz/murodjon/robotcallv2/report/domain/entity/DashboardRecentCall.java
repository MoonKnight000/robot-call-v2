package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

/** One of the period's latest calls, as the dashboard lists it. */
public record DashboardRecentCall(
        long callId,
        String phone,
        String clientName,
        String campaignName,
        String operatorName,
        String direction,
        String disposition,
        int durationSec,
        Instant startedAt,
        boolean hasRecording
) {
}
