package uz.murodjon.uysotvoice.report.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.report.dto.CallRow;
import uz.murodjon.uysotvoice.report.dto.CampaignComparisonRow;
import uz.murodjon.uysotvoice.report.dto.DashboardOutcome;
import uz.murodjon.uysotvoice.report.dto.FunnelStage;
import uz.murodjon.uysotvoice.report.dto.ReportSummary;
import uz.murodjon.uysotvoice.shared.util.DateTimeProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the CSV download for {@code POST /api/reports/calls/export} (§10.4 "⇩ Eksport")
 * and the {@code csv} branch of {@code GET /api/reports/export} (§10.10).
 *
 * <p>The project has CSV <em>parsers</em> ({@code ContactCsvImporter}, {@code
 * TargetCsvImporter}) but no writer yet — this is the first one, kept as small and
 * dependency-free as those readers are.
 */
@Component
public class CsvResponseFactory {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
    private static final String[] HEADER = {
            "call_id", "target_id", "phone", "client_name", "campaign_name", "operator_name",
            "language", "started_at", "ended_at", "duration_sec",
            "disposition", "hangup_cause", "has_recording", "summary", "promised_date",
            "promised_amount", "crm_note_id"
    };

    public ResponseEntity<byte[]> toCsv(String filename, List<CallRow> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, HEADER);
        for (CallRow r : rows) {
            appendRow(sb,
                    String.valueOf(r.callId()),
                    String.valueOf(r.targetId()),
                    r.phone(),
                    r.clientName(),
                    r.campaignName(),
                    r.operatorName(),
                    r.language(),
                    r.startedAt() != null ? r.startedAt().toString() : "",
                    r.endedAt() != null ? r.endedAt().toString() : "",
                    r.durationSec() != null ? String.valueOf(r.durationSec()) : "",
                    r.disposition() != null ? r.disposition().name() : "",
                    r.hangupCause(),
                    String.valueOf(r.hasRecording()),
                    r.summary(),
                    r.promisedDate() != null ? DateTimeProperties.DATE_FORMATTER.format(r.promisedDate()) : "",
                    r.promisedAmount() != null ? r.promisedAmount().toString() : "",
                    r.crmNoteId() != null ? String.valueOf(r.crmNoteId()) : "");
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    /**
     * As {@link #toCsv(String, List)}, for {@code GET /api/reports/export?format=csv}
     * (§10.10) — the whole report bundled as one file, its four sections stacked with a
     * blank line between them since a CSV has no notion of separate sheets.
     */
    public ResponseEntity<byte[]> toCsv(String filename, ReportSummary summary) {
        StringBuilder sb = new StringBuilder();

        appendRow(sb, "section", "metric", "value");
        appendRow(sb, "totals", "from", summary.from().toString());
        appendRow(sb, "totals", "to", summary.to().toString());
        appendRow(sb, "totals", "total_calls", String.valueOf(summary.totals().totalCalls()));
        appendRow(sb, "totals", "answered_calls", String.valueOf(summary.totals().answeredCalls()));
        appendRow(sb, "totals", "avg_duration_sec",
                summary.totals().avgDurationSec() != null ? summary.totals().avgDurationSec().toString() : "");
        appendRow(sb, "totals", "promises", String.valueOf(summary.totals().promises()));
        sb.append("\r\n");

        appendRow(sb, "disposition", "count");
        for (DashboardOutcome o : summary.outcomes()) {
            appendRow(sb, o.disposition(), String.valueOf(o.count()));
        }
        sb.append("\r\n");

        appendRow(sb, "stage", "count", "rate");
        for (FunnelStage f : summary.funnel()) {
            appendRow(sb, f.stage(), String.valueOf(f.count()), String.valueOf(f.rate()));
        }
        sb.append("\r\n");

        appendRow(sb, "campaign_id", "campaign_name", "total_calls", "answered_calls", "answer_rate",
                "avg_duration_sec", "promises");
        for (CampaignComparisonRow c : summary.campaigns()) {
            appendRow(sb, String.valueOf(c.campaignId()), c.campaignName(), String.valueOf(c.totalCalls()),
                    String.valueOf(c.answeredCalls()), String.valueOf(c.answerRate()),
                    c.avgDurationSec() != null ? c.avgDurationSec().toString() : "",
                    String.valueOf(c.promises()));
        }

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static void appendRow(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
    }

    private static String escape(String cell) {
        if (cell == null) {
            return "";
        }
        if (cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
