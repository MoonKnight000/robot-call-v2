package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.application.dto.ReportDownload;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSummary;

public interface ReportExportUseCase {

    /** {@code GET /api/reports/export?format=csv|pdf|xlsx} — the whole summary as one file. */
    ReportDownload exportSummary(long companyId, String format, String from, String to, Long campaignId);

    /** {@code POST /api/reports/calls/export} — the filtered call list as CSV. */
    ReportDownload exportCalls(long companyId, CallFilter filter);

    /** {@code GET /api/reports/calls/{callId}/transcript.txt}. */
    ReportDownload exportTranscript(long companyId, long callId);

    /** The same bytes as {@link #exportSummary}, for a scheduled report's email attachment. */
    byte[] renderSummaryBytes(String format, ReportSummary summary);

    String contentType(String format);
}
