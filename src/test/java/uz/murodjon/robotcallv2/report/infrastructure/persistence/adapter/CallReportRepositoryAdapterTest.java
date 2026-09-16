package uz.murodjon.robotcallv2.report.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.enums.CallTableField;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The call list's WHERE builder and its keyset cursor — pure string and map building, no database. */
class CallReportRepositoryAdapterTest {

    private static final String HEAD = "SELECT 1 FROM call_attempt a";

    /** A named placeholder, not the second colon of a {@code ::} cast. */
    private static final Pattern PLACEHOLDER = Pattern.compile("(?<!:):([a-zA-Z]\\w*)");

    private static CallFilter filter(Integer page, Integer size, String q, List<Long> ids, Long afterId) {
        return new CallFilter(page, size, null, q, null, null, null, null, null, null, ids, afterId);
    }

    @Test
    void pageByNumberUsesTheFilterOrderWithLimitAndOffset() {
        SqlStatement page = CallReportRepositoryAdapter.buildCallPage(7L, null, filter(2, 50, null, null, null));

        assertThat(page.sql())
                .contains("WHERE a.company_id = :companyId")
                .contains("ORDER BY a.id DESC")
                .endsWith("LIMIT :limit OFFSET :offset")
                .doesNotContain(":afterId");
        assertThat(page.params())
                .containsEntry("companyId", 7L)
                .containsEntry("limit", 50)
                .containsEntry("offset", 100L)
                .doesNotContainKey("afterId");
        assertEveryPlaceholderIsBound(page);
    }

    @Test
    void cursorPageReadsBelowTheCursorNewestFirstWithoutOffset() {
        LinkedHashMap<CallTableField, Sort.Direction> orders = new LinkedHashMap<>();
        orders.put(CallTableField.STARTED_AT, Sort.Direction.ASC);
        CallFilter cursor = new CallFilter(3, 20, orders, null, null, null, null, null, null, null, null, 1000L);

        SqlStatement page = CallReportRepositoryAdapter.buildCallPage(7L, null, cursor);

        assertThat(page.sql())
                .endsWith("AND a.id < :afterId ORDER BY a.id DESC LIMIT :limit")
                .doesNotContain("OFFSET")
                .doesNotContain("a.started_at ASC");
        assertThat(page.params())
                .containsEntry("afterId", 1000L)
                .containsEntry("limit", 20)
                .doesNotContainKey("offset");
        assertEveryPlaceholderIsBound(page);
    }

    @Test
    void cursorKeepsTheFilterAndTheCampaign() {
        CallFilter cursor = new CallFilter(null, null, null, "998", null, Disposition.FAILED,
                null, null, null, null, null, 500L);

        SqlStatement page = CallReportRepositoryAdapter.buildCallPage(7L, 3L, cursor);

        assertThat(page.sql()).contains("t.campaign_id = :scopeCampaignId", "LIKE :q", "a.disposition = :disposition",
                "a.id < :afterId");
        assertEveryPlaceholderIsBound(page);
    }

    @Test
    void phoneSearchMatchesWildcardsLiterally() {
        SqlStatement where = CallReportRepositoryAdapter.applyCallFilter(new SqlStatement(HEAD), 7L, null,
                filter(null, null, "50%_!", null, null));

        assertThat(where.sql()).contains("COALESCE(a.phone, t.phone) LIKE :q ESCAPE '!'");
        assertThat(where.params()).containsEntry("q", "%50!%!_!!%");
    }

    @Test
    void blankSearchDoesNotNarrow() {
        SqlStatement where = CallReportRepositoryAdapter.applyCallFilter(new SqlStatement(HEAD), 7L, null,
                filter(null, null, "   ", null, null));

        assertThat(where.sql()).isEqualTo(HEAD + " WHERE a.company_id = :companyId");
        assertThat(where.params()).containsOnlyKeys("companyId");
    }

    @Test
    void explicitIdsReplaceTheFilterButNeverTheCompanyOrTheCampaign() {
        CallFilter selection = new CallFilter(null, null, null, "998", 9L, Disposition.FAILED,
                Instant.parse("2026-09-01T00:00:00Z"), null, 10, null, List.of(1L, 2L), null);

        SqlStatement where = CallReportRepositoryAdapter.applyCallFilter(new SqlStatement(HEAD), 7L, 3L, selection);

        assertThat(where.sql()).isEqualTo(HEAD + " WHERE a.company_id = :companyId"
                + " AND t.campaign_id = :scopeCampaignId AND a.id IN (:ids)");
        assertThat(where.params())
                .containsOnlyKeys("companyId", "scopeCampaignId", "ids")
                .containsEntry("ids", List.of(1L, 2L));
    }

    @Test
    void everyFilterFieldAddsItsConditionTogetherWithItsValue() {
        CallFilter all = new CallFilter(null, null, null, null, 9L, Disposition.NO_ANSWER,
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"), 10, 600, null, null);

        SqlStatement where = CallReportRepositoryAdapter.applyCallFilter(new SqlStatement(HEAD), 7L, null, all);

        assertThat(where.sql()).contains("t.campaign_id = :campaignId", "a.disposition = :disposition",
                "a.started_at >= :dateFrom", "a.started_at < :dateTo",
                "a.duration_sec >= :durationMinSec", "a.duration_sec <= :durationMaxSec");
        assertThat(where.params())
                .containsEntry("campaignId", 9L)
                .containsEntry("disposition", "NO_ANSWER")
                .containsEntry("durationMinSec", 10)
                .containsEntry("durationMaxSec", 600)
                .containsKeys("dateFrom", "dateTo")
                .doesNotContainKey("q");
        assertEveryPlaceholderIsBound(where);
    }

    @Test
    void theCountReadsTheSameCallsAsThePage() {
        CallFilter filter = new CallFilter(1, 20, null, "90", 9L, Disposition.REFUSED, null, null, 5, null, null, null);

        Map<String, Object> pageParams = new HashMap<>(
                CallReportRepositoryAdapter.buildCallPage(7L, 3L, filter).params());
        pageParams.remove("limit");
        pageParams.remove("offset");
        Map<String, Object> countParams =
                CallReportRepositoryAdapter.applyCallFilter(new SqlStatement(HEAD), 7L, 3L, filter).params();

        assertThat(countParams).isEqualTo(pageParams);
    }

    private static void assertEveryPlaceholderIsBound(SqlStatement statement) {
        Matcher matcher = PLACEHOLDER.matcher(statement.sql());
        while (matcher.find()) {
            assertThat(statement.params()).containsKey(matcher.group(1));
        }
    }
}
