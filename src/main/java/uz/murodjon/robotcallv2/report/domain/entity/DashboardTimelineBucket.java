package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

public record DashboardTimelineBucket(
        Instant bucketStart,
        long calls,
        long minutes,
        long failed,
        long completed,
        long missed,
        double avgDurationSec
) {}
