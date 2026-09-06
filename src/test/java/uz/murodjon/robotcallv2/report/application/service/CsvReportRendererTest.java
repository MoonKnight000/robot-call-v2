package uz.murodjon.robotcallv2.report.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReportRendererTest {

    private final CsvReportRenderer renderer = new CsvReportRenderer();

    @Test
    void rendersCallRowsCorrectlyWithEscaping() {
        CallRow row1 = new CallRow(
                1L, 10L, "+998901234567", "uz-UZ",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T10:01:00Z"), 60,
                Disposition.PROMISE_TO_PAY, "NORMAL_CLEARING", true, "Kelishildi\nyangi sana",
                null, null, 100L, "Ali, Valiyev", "Qarz \"Undirish\"", "Bot");

        String csv = new String(renderer.renderCalls(List.of(row1)), StandardCharsets.UTF_8);

        assertThat(renderer.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(csv)
                .contains("call_id,target_id,phone,client_name,campaign_name")
                .contains("\"Ali, Valiyev\"") // comma escaped
                .contains("\"Qarz \"\"Undirish\"\"\"") // quotes escaped
                .contains("\"Kelishildi\nyangi sana\"") // newline escaped
                .contains("+998901234567")
                .contains("PROMISE_TO_PAY");
    }

    @Test
    void rendersReportSummaryWithAllSections() {
        ReportSummary summary = new ReportSummary(
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"),
                null,
                new DashboardTotals(100, 80, 45.5, 30),
                List.of(new DashboardOutcome("PROMISE_TO_PAY", 30), new DashboardOutcome("REFUSAL", 10)),
                List.of(new FunnelStage("CALL", 100, 1.0), new FunnelStage("ANSWERED", 80, 0.8)),
                List.of(new CampaignComparisonRow(1L, "Debt Collection", 100, 80, 0.8, 45.5, 30))
        );

        String csv = new String(renderer.renderBytes(summary), StandardCharsets.UTF_8);

        assertThat(csv)
                .contains("section,metric,value")
                .contains("totals,total_calls,100")
                .contains("totals,answered_calls,80")
                .contains("disposition,count")
                .contains("PROMISE_TO_PAY,30")
                .contains("stage,count,rate")
                .contains("CALL,100,1.0")
                .contains("campaign_id,campaign_name,total_calls")
                .contains("Debt Collection");
    }
}
