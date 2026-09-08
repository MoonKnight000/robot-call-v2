package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

public record DashboardRecentCallRow(
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
) {}
