package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * The ceilings a campaign dials under: lines at once, calls a day, and money.
 *
 * @param maxConcurrentCalls calls the campaign may have in flight at once; 0 for no ceiling of its own
 * @param dailyCallCap       calls it may place in one day; 0 for unlimited
 * @param budgetUzs          spend ceiling for the whole campaign; the dialer pauses it once settled calls reach it, null for none
 * @param dailyBudgetUzs     the same per company-local day; the pause lifts on the operator's resume
 * @param maxCps             new calls it may start in one second, under each trunk's own max_cps; null for the trunks' full rate
 */
public record CampaignLimits(
        int maxConcurrentCalls,
        int dailyCallCap,
        Long budgetUzs,
        Long dailyBudgetUzs,
        Integer maxCps
) {
}
