package uz.murodjon.uysotvoice.report.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.report.dto.CallRow;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the CSV download for {@code POST /api/reports/calls/export} (§10.4 "⇩ Eksport").
 *
 * <p>The project has CSV <em>parsers</em> ({@code ContactCsvImporter}, {@code
 * TargetCsvImporter}) but no writer yet — this is the first one, kept as small and
 * dependency-free as those readers are.
 */
@Component
public class CsvResponseFactory {

    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=UTF-8");
    private static final String[] HEADER = {
            "call_id", "target_id", "phone", "language", "started_at", "ended_at", "duration_sec",
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
                    r.language(),
                    r.startedAt() != null ? r.startedAt().toString() : "",
                    r.endedAt() != null ? r.endedAt().toString() : "",
                    r.durationSec() != null ? String.valueOf(r.durationSec()) : "",
                    r.disposition() != null ? r.disposition().name() : "",
                    r.hangupCause(),
                    String.valueOf(r.hasRecording()),
                    r.summary(),
                    r.promisedDate() != null ? r.promisedDate().toString() : "",
                    r.promisedAmount() != null ? r.promisedAmount().toString() : "",
                    r.crmNoteId() != null ? String.valueOf(r.crmNoteId()) : "");
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
