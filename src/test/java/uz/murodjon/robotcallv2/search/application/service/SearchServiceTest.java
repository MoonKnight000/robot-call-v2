package uz.murodjon.robotcallv2.search.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.ReportedCall;
import uz.murodjon.robotcallv2.role.domain.enums.Permission;
import uz.murodjon.robotcallv2.search.application.dto.SearchItem;
import uz.murodjon.robotcallv2.search.application.dto.SearchResult;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    private static final long COMPANY = 7L;

    private CampaignUseCase campaignUseCase;
    private ReportUseCase reportUseCase;
    private CurrentUser currentUser;
    private SearchService service;

    @BeforeEach
    void setUp() {
        campaignUseCase = mock(CampaignUseCase.class);
        reportUseCase = mock(ReportUseCase.class);
        currentUser = mock(CurrentUser.class);
        service = new SearchService(campaignUseCase, reportUseCase, currentUser);
    }

    @Test
    void aBlankQueryIsRejected() {
        assertThatThrownBy(() -> service.search(COMPANY, "  "))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.SEARCH_QUERY_BLANK));
    }

    /** Each group only for a role that may read it. */
    @Test
    void aGroupTheRoleMayNotReadStaysEmptyAndUnqueried() {
        when(currentUser.hasPermission(Permission.CAMPAIGN_READ)).thenReturn(false);
        when(currentUser.hasPermission(Permission.CALL_READ)).thenReturn(true);
        when(reportUseCase.findRecentCalls(eq(COMPANY), any())).thenReturn(List.of(call(9L, "998901234567")));

        SearchResult result = service.search(COMPANY, "9989");

        assertThat(result.campaigns()).isEmpty();
        assertThat(result.calls()).extracting(SearchItem::id, SearchItem::title)
                .containsExactly(tuple(9L, "998901234567"));
        verifyNoInteractions(campaignUseCase);
    }

    @Test
    void aCallWithoutADispositionShowsWhenItStarted() {
        when(currentUser.hasPermission(Permission.CALL_READ)).thenReturn(true);
        when(reportUseCase.findRecentCalls(eq(COMPANY), any())).thenReturn(List.of(call(9L, "998901234567")));

        assertThat(service.search(COMPANY, "9989").calls().getFirst().subtitle())
                .isEqualTo("2026-10-05T10:00:00Z");
    }

    private static ReportedCall call(long id, String phone) {
        return new ReportedCall(id, 1L, phone, "uz", Instant.parse("2026-10-05T10:00:00Z"), null, null, null,
                null, false, null, null, null, null, null, null, null);
    }
}
