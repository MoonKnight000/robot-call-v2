package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

/** A call that started and has not ended yet. */
public record DashboardLiveCall(
        long callId,
        String phone,
        String clientName,
        String campaignName,
        Instant startedAt,
        String direction
) {
}
