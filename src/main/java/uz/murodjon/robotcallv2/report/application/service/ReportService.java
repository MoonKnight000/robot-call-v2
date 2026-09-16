package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditFilter;
import uz.murodjon.robotcallv2.audit.domain.entity.AuditLog;
import uz.murodjon.robotcallv2.billing.application.port.input.CallBillingUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.campaign.domain.enums.TargetStatus;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionRequest;
import uz.murodjon.robotcallv2.report.application.dto.BulkCallActionResult;
import uz.murodjon.robotcallv2.report.application.dto.CallDetail;
import uz.murodjon.robotcallv2.report.application.dto.CallRow;
import uz.murodjon.robotcallv2.report.application.mapper.ReportMapper;
import uz.murodjon.robotcallv2.report.application.port.input.ReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.CallReportRepository;
import uz.murodjon.robotcallv2.report.application.port.output.CallSourceReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.CallFilter;
import uz.murodjon.robotcallv2.report.domain.entity.CallReport;
import uz.murodjon.robotcallv2.report.domain.entity.CampaignStats;
import uz.murodjon.robotcallv2.report.domain.entity.InboundRouteStats;
import uz.murodjon.robotcallv2.report.domain.entity.PhoneCallHistoryEntry;
import uz.murodjon.robotcallv2.report.domain.entity.ReportedCall;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.dto.DownloadableFile;
import uz.murodjon.robotcallv2.storage.application.port.input.FileStorageUseCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService implements ReportUseCase {

    /** The most calls one CSV export may hold — the whole file is built in memory. */
    private static final int EXPORT_MAX_ROWS = 50_000;

    /** {@code totalElements}/{@code totalPages} of a keyset page, which is not counted. */
    private static final int UNCOUNTED = -1;

    private final CallReportRepository callReportRepository;
    private final CallSourceReportRepository callSourceReportRepository;
    private final ReportMapper reportMapper;
    private final CallBillingUseCase callBillingUseCase;
    private final AuditUseCase audit;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final DoNotCallUseCase doNotCallUseCase;
    private final FileStorageUseCase fileStorageService;

    public ReportService(CallReportRepository callReportRepository,
                         CallSourceReportRepository callSourceReportRepository, ReportMapper reportMapper,
                         CallBillingUseCase callBillingUseCase, AuditUseCase audit,
                         CampaignTargetUseCase campaignTargetUseCase, DoNotCallUseCase doNotCallUseCase,
                         FileStorageUseCase fileStorageService) {
        this.callReportRepository = callReportRepository;
        this.callSourceReportRepository = callSourceReportRepository;
        this.reportMapper = reportMapper;
        this.callBillingUseCase = callBillingUseCase;
        this.audit = audit;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.doNotCallUseCase = doNotCallUseCase;
        this.fileStorageService = fileStorageService;
    }

    /**
     * One snapshot for the report's queries and billing's sum. Under READ COMMITTED every
     * statement sees its own, and a call settling halfway through would show in the spend
     * but not in the counts it is divided by.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CampaignStats findCampaignStats(long companyId, long id) {
        CampaignStats stats = callSourceReportRepository.findCampaignStats(companyId, id);
        if (stats == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, id);
        }
        return stats.withSpend(callBillingUseCase.sumSpentUzsByCampaign(companyId, id, null));
    }

    @Override
    public PageableData<CallRow> findCampaignCalls(long companyId, long id, CallFilter filter) {
        return findCallPage(companyId, id, filter);
    }

    @Override
    public PageableData<CallRow> findCalls(long companyId, CallFilter filter) {
        return findCallPage(companyId, null, filter);
    }

    private PageableData<CallRow> findCallPage(long companyId, Long campaignId, CallFilter filter) {
        if (filter.hasCursor() && !filter.hasDefaultOrder()) {
            // A keyset page can only follow callId downwards; any other order was silently dropped.
            throw new ValidationException(ErrorCode.CALL_CURSOR_ORDER_UNSUPPORTED);
        }
        List<CallRow> rows = callReportRepository.findCalls(companyId, campaignId, filter).stream()
                .map(reportMapper::toCallRow)
                .toList();
        if (filter.hasCursor()) {
            // Not counted: the count reads every matching row, which is the cost the cursor
            // is there to avoid. The next page starts after the last callId here.
            return new PageableData<>(UNCOUNTED, 0, UNCOUNTED, rows);
        }
        long total = callReportRepository.countCalls(companyId, campaignId, filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public List<ReportedCall> findRecentCalls(long companyId, CallFilter filter) {
        return callReportRepository.findCalls(companyId, null, filter);
    }

    @Override
    public List<PhoneCallHistoryEntry> findCallsForPhone(long companyId, String phone, int limit) {
        return callReportRepository.findCallsByPhone(companyId, phone, limit);
    }

    @Override
    public InboundRouteStats findInboundRouteStats(long companyId, long inboundRouteId) {
        return callSourceReportRepository.findInboundRouteStats(companyId, inboundRouteId);
    }

    @Override
    public List<CallRow> exportCalls(long companyId, CallFilter filter) {
        // Refused past the ceiling rather than cut short: the export used to stop at the
        // list page size (500) without a word. One row over tells "exactly the ceiling"
        // apart from "more than it".
        List<ReportedCall> calls = callReportRepository.findCallsForExport(companyId, filter, EXPORT_MAX_ROWS + 1);
        if (calls.size() > EXPORT_MAX_ROWS) {
            throw new ValidationException(ErrorCode.REPORT_EXPORT_TOO_LARGE, EXPORT_MAX_ROWS);
        }
        return calls.stream().map(reportMapper::toCallRow).toList();
    }

    @Override
    public BulkCallActionResult applyBulkAction(long companyId, BulkCallActionRequest request) {
        List<Long> failed = new ArrayList<>();
        int processed = 0;
        for (long callId : request.ids()) {
            ReportedCall call = callReportRepository.findCall(companyId, callId);
            if (call == null) {
                failed.add(callId);
                continue;
            }
            if (applyCallAction(companyId, request.action(), call)) {
                processed++;
            } else {
                failed.add(callId);
            }
        }
        audit.record(companyId, "CALLS_BULK_" + request.action().toUpperCase(), "call", null,
                processed + " processed, " + failed.size() + " failed");
        return new BulkCallActionResult(processed, failed);
    }

    private boolean applyCallAction(long companyId, String action, ReportedCall call) {
        return switch (action) {
            case "retry" -> {
                CampaignTarget target = campaignTargetUseCase.findTarget(companyId, call.targetId());
                if (target == null) {
                    yield false;
                }
                campaignTargetUseCase.updateTargetStatus(target.id(), TargetStatus.PENDING, Instant.now());
                yield true;
            }
            case "dnc" -> {
                if (call.phone() == null || call.phone().isBlank()) {
                    yield false;
                }
                doNotCallUseCase.add(companyId, call.phone(), "opted out via bulk action", DoNotCallSource.MANUAL);
                yield true;
            }
            default -> throw new ValidationException(ErrorCode.REPORT_BULK_ACTION_UNKNOWN, action);
        };
    }

    @Override
    public CallDetail findCallDetail(long companyId, long callId) {
        CallReport report = callReportRepository.findCallReport(companyId, callId);
        if (report == null) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, callId);
        }
        return reportMapper.toCallDetail(report);
    }

    @Override
    public DownloadableFile findRecording(long companyId, long callId) {
        Long fileId = callReportRepository.findRecordingFileId(companyId, callId);
        if (fileId == null) {
            throw new NotFoundException(ErrorCode.CALL_RECORDING_NOT_FOUND, callId);
        }
        audit.record(companyId, "RECORDING_DOWNLOAD", "call", String.valueOf(callId), String.valueOf(fileId));
        return fileStorageService.download(companyId, fileId);
    }

    @Override
    public PageableData<AuditLog> findAuditLog(long companyId, AuditFilter filter) {
        List<AuditLog> rows = audit.findRecent(companyId, filter);
        long total = audit.count(companyId, filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }
}
