package uz.murodjon.robotcallv2.report.application.port.input;

import uz.murodjon.robotcallv2.report.domain.entity.CriterionTrendPoint;
import uz.murodjon.robotcallv2.report.domain.entity.FailedCriterion;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorQualityScore;
import uz.murodjon.robotcallv2.report.domain.entity.TopicCount;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;

import java.util.List;

/**
 * Operator call quality reports (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.4), read from the
 * scorecards. {@code from}/{@code to} as in the contact-centre reports: an ISO instant or a
 * company-zone date, the last seven days when omitted.
 */
public interface QualityReportUseCase {

    List<OperatorQualityScore> findOperatorRating(long companyId, String from, String to);

    /** @param bucket null for days */
    List<CriterionTrendPoint> findCriterionTrend(long companyId, String from, String to, TrendBucket bucket,
                                                 Long operatorUserId);

    List<FailedCriterion> findFailedCriteria(long companyId, String from, String to, Long operatorUserId);

    /** @param limit null for 20; kept within 1–100 */
    List<TopicCount> findTopics(long companyId, String from, String to, Long operatorUserId, Integer limit);
}
