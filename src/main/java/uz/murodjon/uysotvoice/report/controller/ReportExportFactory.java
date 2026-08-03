package uz.murodjon.uysotvoice.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.report.dto.ReportSummary;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

/**
 * Picks the response shape for {@code GET /api/reports/export?format=...} (§10.10
 * "⇩ Hisobotni yuklab olish"). Which renderer runs is a web/format concern, not
 * business logic and not a controller's single delegation — the same reasoning
 * {@link RecordingResponseFactory} documents for its own two-shape choice.
 */
@Component
public class ReportExportFactory {

    private final CsvResponseFactory csv;
    private final PdfResponseFactory pdf;
    private final XlsxResponseFactory xlsx;

    public ReportExportFactory(CsvResponseFactory csv, PdfResponseFactory pdf, XlsxResponseFactory xlsx) {
        this.csv = csv;
        this.pdf = pdf;
        this.xlsx = xlsx;
    }

    public ResponseEntity<byte[]> toResponse(String format, ReportSummary summary) {
        return switch (format == null ? "" : format.toLowerCase()) {
            case "csv" -> csv.toCsv("report.csv", summary);
            case "pdf" -> pdf.toResponse("report.pdf", summary);
            case "xlsx" -> xlsx.toResponse("report.xlsx", summary);
            default -> throw new ValidationException(
                    "Unknown export format '" + format + "' — use csv, pdf, or xlsx");
        };
    }
}
