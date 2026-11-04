package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatHistoryUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.report.application.dto.OperatorReportRow;
import uz.murodjon.robotcallv2.report.application.dto.QueueReportRow;
import uz.murodjon.robotcallv2.report.application.mapper.ReportMapper;
import uz.murodjon.robotcallv2.report.application.port.input.ContactCenterReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.CallReportRepository;
import uz.murodjon.robotcallv2.report.application.port.output.ContactCenterReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardRange;
import uz.murodjon.robotcallv2.report.domain.entity.PredictiveDialingStats;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;
import java.util.List;

/**
 * Contact-centre reports (docs/INBOUND-PBX-PLAN.md §11): how each queue served its callers
 * — service level, speed of answer, abandon rate — and how each operator spent the day.
 */
@Service
public class ContactCenterReportService implements ContactCenterReportUseCase {

    private final ContactCenterReportRepository contactCenterReportRepository;
    private final CallReportRepository callReportRepository;
    private final ReportMapper reportMapper;
    private final CallEventUseCase callEventUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final CampaignUseCase campaignUseCase;
    private final Clock clock;
    private final ChatHistoryUseCase chatHistoryUseCase;
    private final AuditUseCase auditUseCase;

    public ContactCenterReportService(ContactCenterReportRepository contactCenterReportRepository,
                                      CallReportRepository callReportRepository, ReportMapper reportMapper,
                                      CallEventUseCase callEventUseCase, CompanyConfigUseCase companyConfigUseCase,
                                      CampaignUseCase campaignUseCase, Clock clock, ChatHistoryUseCase chatHistoryUseCase,
                                      AuditUseCase auditUseCase) {
        this.campaignUseCase = campaignUseCase;
        this.contactCenterReportRepository = contactCenterReportRepository;
        this.callReportRepository = callReportRepository;
        this.reportMapper = reportMapper;
        this.callEventUseCase = callEventUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.clock = clock;
        this.chatHistoryUseCase = chatHistoryUseCase;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public List<QueueReportRow> findQueueReport(long companyId, String from, String to) {
        DashboardRange range = findRange(companyId, from, to);
        return contactCenterReportRepository.findQueuePerformance(companyId, range.from(), range.to()).stream()
                .map(reportMapper::toQueueReportRow)
                .toList();
    }

    @Override
    public List<OperatorReportRow> findOperatorReport(long companyId, String from, String to) {
        DashboardRange range = findRange(companyId, from, to);
        return contactCenterReportRepository.findOperatorPerformance(companyId, range.from(), range.to()).stream()
                .map(reportMapper::toOperatorReportRow)
                .toList();
    }

    @Override
    public PredictiveDialingStats findPredictiveDialingStats(long companyId, long campaignId, String from, String to) {
        if (campaignUseCase.findCampaign(companyId, campaignId) == null) {
            throw new NotFoundException(ErrorCode.CAMPAIGN_NOT_FOUND, campaignId);
        }
        DashboardRange range = findRange(companyId, from, to);
        return contactCenterReportRepository.findPredictiveDialingStats(companyId, campaignId, range.from(), range.to());
    }

    @Override
    public List<CallEvent> findCallEvents(long companyId, long callId) {
        return findEvents(companyId, callId, false);
    }

    @Override
    public List<CallEvent> findCallTrace(long companyId, long callId) {
        List<CallEvent> trace = findEvents(companyId, callId, true);
        // The trace carries the CRM data the functions sent and received: who opened it is on record.
        auditUseCase.record(companyId, "CALL_TRACE_VIEW", "call", String.valueOf(callId), null);
        return trace;
    }

    @Override
    public List<CallEvent> findChatTrace(long companyId, long chatId) {
        chatHistoryUseCase.requireChat(companyId, chatId);
        auditUseCase.record(companyId, "CHAT_TRACE_VIEW", "chat", String.valueOf(chatId), null);
        return callEventUseCase.findByChatSessionId(companyId, chatId);
    }

    /** The timeline split in two: the trace carries CRM data and needs its own right. */
    private List<CallEvent> findEvents(long companyId, long callId, boolean trace) {
        if (callReportRepository.findCall(companyId, callId) == null) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, callId);
        }
        return callEventUseCase.findByCallAttemptId(companyId, callId).stream()
                .filter(event -> event.type().isCallTrace() == trace)
                .toList();
    }

    /** A bare date in the range is the company's day, not UTC's. */
    private DashboardRange findRange(long companyId, String from, String to) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return DashboardRange.of(from, to, config != null ? config.zoneId() : clock.getZone(), clock.instant());
    }
}
