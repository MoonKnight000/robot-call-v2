package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.report.application.dto.RoiReportResponse;
import uz.murodjon.robotcallv2.report.application.port.input.RoiReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.RoiReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardRange;
import uz.murodjon.robotcallv2.report.domain.entity.RoiAssumptions;
import uz.murodjon.robotcallv2.report.domain.entity.RoiTotals;
import uz.murodjon.robotcallv2.report.domain.service.RoiCalculator;
import uz.murodjon.robotcallv2.report.infrastructure.config.RoiReportProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

/** The ROI page: what the robot did and cost against what operators would have cost. */
@Service
public class RoiReportService implements RoiReportUseCase {

    private final RoiReportRepository roiReportRepository;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final RoiReportProperties roiReportProperties;
    private final Clock clock;

    public RoiReportService(RoiReportRepository roiReportRepository, CompanyConfigUseCase companyConfigUseCase,
                            RoiReportProperties roiReportProperties, Clock clock) {
        this.roiReportRepository = roiReportRepository;
        this.companyConfigUseCase = companyConfigUseCase;
        this.roiReportProperties = roiReportProperties;
        this.clock = clock;
    }

    @Override
    public RoiReportResponse findRoiReport(long companyId, String from, String to, Long campaignId,
                                           Long operatorHourlyCostUzs) {
        if (operatorHourlyCostUzs != null && operatorHourlyCostUzs <= 0) {
            throw new ValidationException(ErrorCode.REPORT_OPERATOR_COST_INVALID, operatorHourlyCostUzs);
        }
        DashboardRange range = DashboardRange.of(from, to, findZone(companyId), clock.instant());
        RoiTotals totals = roiReportRepository.findRoiTotals(companyId, range.from(), range.to(), campaignId);
        RoiAssumptions assumptions = new RoiAssumptions(
                operatorHourlyCostUzs != null ? operatorHourlyCostUzs : roiReportProperties.operatorHourlyCostUzs(),
                roiReportProperties.operatorSecondsPerAttempt(),
                roiReportProperties.operatorHoursPerMonth());
        double periodDays = Duration.between(range.from(), range.to()).toMinutes() / (24.0 * 60.0);
        return new RoiReportResponse(range.from(), range.to(), totals,
                RoiCalculator.calculate(totals, assumptions, periodDays), assumptions);
    }

    /** The company's own days, so "2026-10-01" starts at Tashkent midnight, not UTC's. */
    private ZoneId findZone(long companyId) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return config != null ? config.zoneId() : clock.getZone();
    }
}
