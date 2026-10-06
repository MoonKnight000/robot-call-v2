package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.report.application.dto.OperatorReportRow;
import uz.murodjon.robotcallv2.report.application.dto.QueueReportRow;
import uz.murodjon.robotcallv2.report.application.mapper.ReportMapper;
import uz.murodjon.robotcallv2.report.application.port.input.ContactCenterReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.CallReportRepository;
import uz.murodjon.robotcallv2.report.application.port.output.ContactCenterReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardRange;
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
    private final Clock clock;

    public ContactCenterReportService(ContactCenterReportRepository contactCenterReportRepository,
                                      CallReportRepository callReportRepository, ReportMapper reportMapper,
                                      CallEventUseCase callEventUseCase, CompanyConfigUseCase companyConfigUseCase,
                                      Clock clock) {
        this.contactCenterReportRepository = contactCenterReportRepository;
        this.callReportRepository = callReportRepository;
        this.reportMapper = reportMapper;
        this.callEventUseCase = callEventUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.clock = clock;
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
    public List<CallEvent> findCallEvents(long companyId, long callId) {
        if (callReportRepository.findCall(companyId, callId) == null) {
            throw new NotFoundException(ErrorCode.CALL_NOT_FOUND, callId);
        }
        return callEventUseCase.findByCallAttemptId(companyId, callId);
    }

    /** A bare date in the range is the company's day, not UTC's. */
    private DashboardRange findRange(long companyId, String from, String to) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return DashboardRange.of(from, to, config != null ? config.zoneId() : clock.getZone(), clock.instant());
    }
}
