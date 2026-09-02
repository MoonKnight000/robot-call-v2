package uz.murodjon.robotcallv2.report.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvResponseFactoryTest {

    private final CsvResponseFactory factory = new CsvResponseFactory();

    @Test
    void toCsvRendersCallRowsCorrectlyWithEscaping() {
        CallRow row1 = new CallRow(
                1L, 10L, "+998901234567", "uz-UZ",
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T10:01:00Z"), 60,
                Disposition.PROMISE_TO_PAY, "NORMAL_CLEARING", true, "Kelishildi\nyangi sana",
                null, null, 100L, "Ali, Valiyev", "Qarz \"Undirish\"", "Bot");

        ResponseEntity<byte[]> response = factory.toCsv("calls.csv", List.of(row1));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"calls.csv\"");

        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertThat(csv)
                .contains("call_id,target_id,phone,client_name,campaign_name")
                .contains("\"Ali, Valiyev\"") // comma escaped
                .contains("\"Qarz \"\"Undirish\"\"\"") // quotes escaped
                .contains("\"Kelishildi\nyangi sana\"") // newline escaped
                .contains("+998901234567")
                .contains("PROMISE_TO_PAY");
    }

    @Test
    void toCsvRendersReportSummaryWithAllSections() {
        ReportSummary summary = new ReportSummary(
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"),
                null,
                new DashboardTotals(100, 80, 45.5, 30),
                List.of(new DashboardOutcome("PROMISE_TO_PAY", 30), new DashboardOutcome("REFUSAL", 10)),
                List.of(new FunnelStage("CALL", 100, 1.0), new FunnelStage("ANSWERED", 80, 0.8)),
                List.of(new CampaignComparisonRow(1L, "Debt Collection", 100, 80, 0.8, 45.5, 30))
        );

        ResponseEntity<byte[]> response = factory.toCsv("summary.csv", summary);
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

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
