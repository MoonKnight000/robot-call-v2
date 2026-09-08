package uz.murodjon.robotcallv2.report.application.dto;

import java.util.List;

public record DashboardSummaryResponse(
        String range,
        String from,
        String to,
        String updatedAt,
        List<DashboardKpiCard> kpis,
        DashboardTimeline timeline,
        List<StatusBreakdownItem> statusBreakdown,
        DirectionMix directionMix,
        List<TopAgentItem> topAgents,
        List<DashboardCampaignItem> campaigns,
        List<DashboardLiveItem> live,
        List<DashboardRecentCallItem> recentCalls
) {}
