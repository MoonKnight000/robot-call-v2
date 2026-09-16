package uz.murodjon.robotcallv2.search.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportedCall;
import uz.murodjon.robotcallv2.search.application.dto.SearchItem;
import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.search.application.port.input.SearchUseCase;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.search.domain.service.SearchValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;

import java.util.List;

/**
 * Command palette backend (§11.9) UseCase implementation.
 */
@Service
public class SearchService implements SearchUseCase {

    private static final int GROUP_LIMIT = 5;

    private final CampaignUseCase campaignUseCase;
    private final ReportUseCase reportUseCase;
    private final CurrentUser currentUser;

    public SearchService(CampaignUseCase campaignUseCase, ReportUseCase reportUseCase,
                         CurrentUser currentUser) {
        this.campaignUseCase = campaignUseCase;
        this.reportUseCase = reportUseCase;
        this.currentUser = currentUser;
    }

    /** Each group only for a caller whose role may read it (the endpoint needs either). */
    @Override
    public SearchResult search(long companyId, String q) {
        SearchValidator.validateQuery(q);

        List<SearchItem> campaignResults = !currentUser.hasPermission(Permission.CAMPAIGN_READ) ? List.of()
                : campaignUseCase.findCampaignsByName(companyId, q, GROUP_LIMIT).stream()
                .map(c -> new SearchItem(c.id(), c.name(), c.status().name()))
                .toList();
        CallFilter callFilter = new CallFilter(0, GROUP_LIMIT, null, q, null, null, null, null, null, null, null, null);
        List<SearchItem> callResults = !currentUser.hasPermission(Permission.CALL_READ) ? List.of()
                : reportUseCase.findRecentCalls(companyId, callFilter).stream()
                .map(SearchService::toCallItem)
                .toList();
        return new SearchResult(campaignResults, callResults);
    }

    private static SearchItem toCallItem(ReportedCall row) {
        String context = row.disposition() == null ? row.startedAt().toString() : row.disposition().name();
        return new SearchItem(row.callId(), row.phone(), context);
    }
}

