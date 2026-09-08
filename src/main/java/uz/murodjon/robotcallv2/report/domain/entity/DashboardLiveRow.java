package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

public record DashboardLiveRow(
        long callId,
        String phone,
        String clientName,
        String campaignName,
        Instant startedAt,
        String direction
) {}
