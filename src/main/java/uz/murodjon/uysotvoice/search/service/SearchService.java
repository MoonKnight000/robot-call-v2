package uz.murodjon.uysotvoice.search.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.campaign.repository.CampaignRepository;
import uz.murodjon.uysotvoice.report.dto.CallFilter;
import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.repository.ReportRepository;
import uz.murodjon.uysotvoice.search.dto.SearchItem;
import uz.murodjon.uysotvoice.search.dto.SearchResult;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.util.List;

/**
 * Command palette backend (UI-DESIGN §11.9) — reuses the existing campaign-name and
 * call-phone search paths ({@link CampaignRepository#searchByName},
 * {@link ReportRepository#recentCalls} with {@link CallFilter#q()}) rather than building
 * a separate index; both are already company-scoped.
 */
@Service
public class SearchService {

    private static final int GROUP_LIMIT = 5;

    private final CampaignRepository campaigns;
    private final ReportRepository reports;

    public SearchService(CampaignRepository campaigns, ReportRepository reports) {
        this.campaigns = campaigns;
        this.reports = reports;
    }

    public SearchResult search(String q) {
        if (q == null || q.isBlank()) {
            throw new ValidationException("q: must not be blank");
        }
        List<SearchItem> campaignResults = campaigns.searchByName(q, GROUP_LIMIT).stream()
                .map(c -> new SearchItem(c.id(), c.name(), c.status().name()))
                .toList();
        CallFilter callFilter = new CallFilter(0, GROUP_LIMIT, null, q, null, null, null, null, null, null, null);
        List<SearchItem> callResults = reports.recentCalls(callFilter).stream()
                .map(SearchService::toCallItem)
                .toList();
        return new SearchResult(campaignResults, callResults);
    }

    private static SearchItem toCallItem(CallRow row) {
        String context = row.disposition() == null ? row.startedAt().toString() : row.disposition().name();
        return new SearchItem(row.callId(), row.phone(), context);
    }
}
