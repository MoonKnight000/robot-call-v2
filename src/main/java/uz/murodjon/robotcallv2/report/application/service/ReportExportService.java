package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.application.dto.ReportDownload;
import uz.murodjon.robotcallv2.report.application.port.input.ReportExportUseCase;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.CallDetail;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * Picks the renderer for {@code GET /api/reports/export?format=...} (§10.10
 * "⇩ Hisobotni yuklab olish") and hands back a finished file. Which renderer runs is a
 * format decision, so it lives here rather than in the controller.
 */
@Service
public class ReportExportService implements ReportExportUseCase {

    private final ReportUseCase reportUseCase;
    private final CsvReportRenderer csvReportRenderer;
    private final PdfReportRenderer pdfReportRenderer;
    private final XlsxReportRenderer xlsxReportRenderer;
    private final TranscriptRenderer transcriptRenderer;

    public ReportExportService(ReportUseCase reportUseCase,
                               CsvReportRenderer csvReportRenderer,
                               PdfReportRenderer pdfReportRenderer,
                               XlsxReportRenderer xlsxReportRenderer,
                               TranscriptRenderer transcriptRenderer) {
        this.reportUseCase = reportUseCase;
        this.csvReportRenderer = csvReportRenderer;
        this.pdfReportRenderer = pdfReportRenderer;
        this.xlsxReportRenderer = xlsxReportRenderer;
        this.transcriptRenderer = transcriptRenderer;
    }

    @Override
    public ReportDownload exportSummary(long companyId, String format, String from, String to, Long campaignId) {
        ReportSummary summary = reportUseCase.summary(companyId, from, to, campaignId);
        return new ReportDownload("report." + normalize(format), contentType(format),
                renderSummaryBytes(format, summary));
    }

    @Override
    public ReportDownload exportCalls(long companyId, CallFilter filter) {
        return new ReportDownload("calls.csv", csvReportRenderer.contentType(),
                csvReportRenderer.renderCalls(reportUseCase.exportCalls(companyId, filter)));
    }

    @Override
    public ReportDownload exportTranscript(long companyId, long callId) {
        CallDetail detail = reportUseCase.call(companyId, callId);
        return new ReportDownload("call-" + callId + "-transcript.txt", transcriptRenderer.contentType(),
                transcriptRenderer.renderBytes(detail));
    }

    @Override
    public byte[] renderSummaryBytes(String format, ReportSummary summary) {
        return switch (normalize(format)) {
            case "csv" -> csvReportRenderer.renderBytes(summary);
            case "pdf" -> pdfReportRenderer.renderBytes(summary);
            case "xlsx" -> xlsxReportRenderer.renderBytes(summary);
            default -> throw new ValidationException(ErrorCode.REPORT_EXPORT_FORMAT_UNKNOWN, format);
        };
    }

    @Override
    public String contentType(String format) {
        return switch (normalize(format)) {
            case "csv" -> csvReportRenderer.contentType();
            case "pdf" -> pdfReportRenderer.contentType();
            case "xlsx" -> xlsxReportRenderer.contentType();
            default -> throw new ValidationException(ErrorCode.REPORT_EXPORT_FORMAT_UNKNOWN, format);
        };
    }

    private static String normalize(String format) {
        return format == null ? "" : format.toLowerCase();
    }
}
