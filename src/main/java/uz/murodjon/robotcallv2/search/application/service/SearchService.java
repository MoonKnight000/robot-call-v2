package uz.murodjon.robotcallv2.search.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.campaign.application.port.output.CampaignRepository;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.CallRow;
import uz.murodjon.robotcallv2.search.application.dto.SearchItem;
import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.search.application.port.input.SearchUseCase;
import uz.murodjon.robotcallv2.search.domain.service.SearchValidator;

import java.util.List;

/**
 * Command palette backend (§11.9) UseCase implementation.
 */
@Service
public class SearchService implements SearchUseCase {

    private static final int GROUP_LIMIT = 5;

    private final CampaignRepository campaignRepository;
    private final ReportRepository reportRepository;

    public SearchService(CampaignRepository campaignRepository, ReportRepository reportRepository) {
        this.campaignRepository = campaignRepository;
        this.reportRepository = reportRepository;
    }

    @Override
    public SearchResult search(long companyId, String q) {
        SearchValidator.validateQuery(q);

        List<SearchItem> campaignResults = campaignRepository.searchByName(companyId, q, GROUP_LIMIT).stream()
                .map(c -> new SearchItem(c.id(), c.name(), c.status().name()))
                .toList();
        CallFilter callFilter = new CallFilter(0, GROUP_LIMIT, null, q, null, null, null, null, null, null, null);
        List<SearchItem> callResults = reportRepository.recentCalls(companyId, callFilter).stream()
                .map(SearchService::toCallItem)
                .toList();
        return new SearchResult(campaignResults, callResults);
    }

    private static SearchItem toCallItem(CallRow row) {
        String context = row.disposition() == null ? row.startedAt().toString() : row.disposition().name();
        return new SearchItem(row.callId(), row.phone(), context);
    }
}

