package uz.murodjon.robotcallv2.dialer.application.dto;

import java.util.List;

/**
 * A campaign's dialing capacity ({@code GET /api/concurrency/campaigns/{campaignId}}) — Retell's "Dialing capacity":
 * how many calls it may hold at once against the company's whole limit, and how fast it may start them.
 *
 * @param companyLimit   calls the company may hold at once (its plan, or the platform default)
 * @param campaignLimit  this campaign's own ceiling ({@code limits.maxConcurrentCalls}); 0 = only the company's
 * @param otherCampaigns the ceilings of the company's other ACTIVE campaigns added up — they share {@code companyLimit}
 * @param companyCps     calls per second over all the company's enabled trunks
 * @param campaignMaxCps this campaign's own CPS cap ({@code limits.maxCps}); null = the full {@code companyCps}
 * @param trunks         each enabled trunk's own rate, what {@code companyCps} adds up
 */
public record CampaignCapacityResponse(
        int companyLimit,
        int campaignLimit,
        int otherCampaigns,
        int companyCps,
        Integer campaignMaxCps,
        List<TrunkCpsRow> trunks
) {
}
