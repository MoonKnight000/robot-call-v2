package uz.murodjon.robotcallv2.report.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.report.application.port.input.QualityReportUseCase;
import uz.murodjon.robotcallv2.report.application.port.output.QualityReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.CriterionTrendPoint;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardRange;
import uz.murodjon.robotcallv2.report.domain.entity.FailedCriterion;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorQualityScore;
import uz.murodjon.robotcallv2.report.domain.entity.TopicCount;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;

import java.time.Clock;
import java.util.List;

/**
 * Operator call quality (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.4): the operator rating, how
 * each criterion is met over time, the criteria broken most, and what the calls were about —
 * all read from the scorecards, with a supervisor's corrections counted.
 */
@Service
public class QualityReportService implements QualityReportUseCase {

    private static final int DEFAULT_TOPIC_LIMIT = 20;
    private static final int MAX_TOPIC_LIMIT = 100;

    private final QualityReportRepository qualityReportRepository;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final Clock clock;

    public QualityReportService(QualityReportRepository qualityReportRepository,
                                CompanyConfigUseCase companyConfigUseCase, Clock clock) {
        this.qualityReportRepository = qualityReportRepository;
        this.companyConfigUseCase = companyConfigUseCase;
        this.clock = clock;
    }

    @Override
    public List<OperatorQualityScore> findOperatorRating(long companyId, String from, String to) {
        DashboardRange range = findRange(companyId, from, to);
        return qualityReportRepository.findOperatorQualityScores(companyId, range.from(), range.to());
    }

    @Override
    public List<CriterionTrendPoint> findCriterionTrend(long companyId, String from, String to, TrendBucket bucket,
                                                        Long operatorUserId) {
        DashboardRange range = findRange(companyId, from, to);
        return qualityReportRepository.findCriterionTrend(companyId, range.from(), range.to(), range.zone(),
                bucket != null ? bucket : TrendBucket.DAY, operatorUserId);
    }

    @Override
    public List<FailedCriterion> findFailedCriteria(long companyId, String from, String to, Long operatorUserId) {
        DashboardRange range = findRange(companyId, from, to);
        return qualityReportRepository.findFailedCriteria(companyId, range.from(), range.to(), operatorUserId);
    }

    @Override
    public List<TopicCount> findTopics(long companyId, String from, String to, Long operatorUserId, Integer limit) {
        DashboardRange range = findRange(companyId, from, to);
        int size = limit == null ? DEFAULT_TOPIC_LIMIT : Math.clamp(limit, 1, MAX_TOPIC_LIMIT);
        return qualityReportRepository.findTopicCounts(companyId, range.from(), range.to(), operatorUserId, size);
    }

    /** A bare date in the range is the company's day, not UTC's. */
    private DashboardRange findRange(long companyId, String from, String to) {
        CompanyConfig config = companyConfigUseCase.find(companyId);
        return DashboardRange.of(from, to, config != null ? config.zoneId() : clock.getZone(), clock.instant());
    }
}
