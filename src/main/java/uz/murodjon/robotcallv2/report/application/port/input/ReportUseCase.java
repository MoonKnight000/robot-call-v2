package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.CallDetail;
import uz.murodjon.robotcallv2.report.application.dto.CallRow;
import uz.murodjon.robotcallv2.report.domain.entity.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;

import java.util.List;

/** The call list, one call, a campaign's and a route's figures, and the audit log. */
public interface ReportUseCase {

    CampaignStats findCampaignStats(long companyId, long id);

    PageableData<CallRow> findCampaignCalls(long companyId, long id, CallFilter filter);

    /**
     * One page of calls. With {@link CallFilter#afterId()} set it is a keyset page: newest
     * first below that id, and not counted — {@code totalElements} and {@code totalPages}
     * come back as {@code -1}.
     */
    PageableData<CallRow> findCalls(long companyId, CallFilter filter);

    List<CallRow> exportCalls(long companyId, CallFilter filter);

    BulkCallActionResult applyBulkAction(long companyId, BulkCallActionRequest request);

    CallDetail findCallDetail(long companyId, long callId);

    DownloadableFile findRecording(long companyId, long callId);

    PageableData<AuditLog> findAuditLog(long companyId, AuditFilter filter);

    /** One page of calls without the total count — for a short list such as search results. */
    List<ReportedCall> findRecentCalls(long companyId, CallFilter filter);

    List<PhoneCallHistoryEntry> findCallsForPhone(long companyId, String phone, int limit);

    InboundRouteStats findInboundRouteStats(long companyId, long inboundRouteId);
}
