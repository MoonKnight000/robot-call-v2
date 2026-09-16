package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.CallReport;
import uz.murodjon.robotcallv2.report.domain.entity.PhoneCallHistoryEntry;
import uz.murodjon.robotcallv2.report.domain.entity.ReportedCall;

import java.util.List;

/** The call list, one call in full, and a number's call history — read-only. */
public interface CallReportRepository {

    /**
     * One page of the company's calls matching the filter, narrowed to one campaign when
     * {@code campaignId} is set: by page number, or after {@link CallFilter#afterId()} when
     * the filter carries a cursor.
     */
    List<ReportedCall> findCalls(long companyId, Long campaignId, CallFilter filter);

    /** How many calls {@link #findCalls} pages through; the cursor plays no part in it. */
    long countCalls(long companyId, Long campaignId, CallFilter filter);

    /** The filter's calls in its order, at most {@code limit} of them — no paging, no cursor. */
    List<ReportedCall> findCallsForExport(long companyId, CallFilter filter, int limit);

    /** The call, or null when there is none in this company. */
    ReportedCall findCall(long companyId, long callId);

    /** The call with its transcript, result and technical data, or null when there is none. */
    CallReport findCallReport(long companyId, long callId);

    /** The call's recording, or null when the call or its recording is missing. */
    Long findRecordingFileId(long companyId, long callId);

    /** Calls to that number, newest first, at most {@code limit}. */
    List<PhoneCallHistoryEntry> findCallsByPhone(long companyId, String phone, int limit);
}
