package uz.murodjon.robotcallv2.report.domain.entity;

/** How far through its list a campaign is: targets settled out of all its targets. */
public record DashboardCampaignProgress(
        long id,
        String name,
        long totalTargets,
        long doneTargets
) {
}
