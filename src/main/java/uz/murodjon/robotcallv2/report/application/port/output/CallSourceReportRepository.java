package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.CampaignStats;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;

/** All-time figures of one source of calls: a campaign dialling out, or a DID taking calls in. */
public interface CallSourceReportRepository {

    /**
     * The campaign's figures, or null when there is no such campaign in this company. The
     * spend fields are left at zero: the ledger is billing's — see
     * {@link CampaignStats#withSpend(long)}.
     */
    CampaignStats findCampaignStats(long companyId, long campaignId);

    /** The route's figures, or null when there is no such route in this company. */
    InboundRouteStats findInboundRouteStats(long companyId, long inboundRouteId);
}
