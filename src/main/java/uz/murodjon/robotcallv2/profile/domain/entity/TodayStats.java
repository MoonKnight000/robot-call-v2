package uz.murodjon.robotcallv2.profile.domain.entity;

public record TodayStats(
        long totalCalls,
        long answeredCalls,
        double onAirMinutes,
        double qualityPct
) {
}
