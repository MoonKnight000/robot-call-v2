package uz.murodjon.robotcallv2.dialer.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.dialer.application.dto.CampaignCapacityResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.TrunkCpsRow;
import uz.murodjon.robotcallv2.dialer.application.port.input.CampaignCapacityUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.ConcurrencyUseCase;
import uz.murodjon.robotcallv2.dialer.infrastructure.config.DialerProperties;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.util.List;

/**
 * A campaign's dialing capacity for the campaign page's Numbers tab: the company's concurrent-call limit, what the
 * campaign and the company's other running campaigns take of it, and the trunks' calls per second.
 */
@Service
public class CampaignCapacityService implements CampaignCapacityUseCase {

    private final CampaignUseCase campaignUseCase;
    private final ConcurrencyUseCase concurrencyUseCase;
    private final SipTrunkUseCase sipTrunkUseCase;
    private final DialerProperties dialerProperties;

    public CampaignCapacityService(CampaignUseCase campaignUseCase, ConcurrencyUseCase concurrencyUseCase,
                                   SipTrunkUseCase sipTrunkUseCase, DialerProperties dialerProperties) {
        this.campaignUseCase = campaignUseCase;
        this.concurrencyUseCase = concurrencyUseCase;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.dialerProperties = dialerProperties;
    }

    @Override
    public CampaignCapacityResponse findCampaignCapacity(long companyId, long campaignId) {
        Campaign campaign = campaignUseCase.requireCampaign(companyId, campaignId);
        int otherCampaigns = campaignUseCase.findActiveCampaigns().stream()
                .filter(c -> c.companyId() == companyId && c.id() != campaignId)
                .mapToInt(c -> c.limits().maxConcurrentCalls())
                .sum();
        List<TrunkCpsRow> trunks = sipTrunkUseCase.findAllEnabledForCompany(companyId).stream()
                .map(t -> new TrunkCpsRow(t.id(), t.name(),
                        t.maxCps() != null ? t.maxCps() : dialerProperties.defaultTrunkCps(), t.maxCps() == null))
                .toList();
        return new CampaignCapacityResponse(
                concurrencyUseCase.findConcurrency(companyId).limit(),
                campaign.limits().maxConcurrentCalls(),
                otherCampaigns,
                trunks.stream().mapToInt(TrunkCpsRow::maxCps).sum(),
                campaign.limits().maxCps(),
                trunks);
    }
}
