package uz.murodjon.robotcallv2.report.application.port.output;

import uz.murodjon.robotcallv2.report.domain.entity.CriterionTrendPoint;
import uz.murodjon.robotcallv2.report.domain.entity.FailedCriterion;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorQualityScore;
import uz.murodjon.robotcallv2.report.domain.entity.TopicCount;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Operator call quality over the scorecards made in [from, to). {@code operatorUserId}, where
 * taken, narrows to one operator's calls; null is every operator's.
 */
public interface QualityReportRepository {

    /** Every operator with a SIP account, scored calls or not — best average score first. */
    List<OperatorQualityScore> findOperatorQualityScores(long companyId, Instant from, Instant to);

    /** One point per criterion per day or week with an answer, in the company's zone. */
    List<CriterionTrendPoint> findCriterionTrend(long companyId, Instant from, Instant to, ZoneId zone,
                                                 TrendBucket bucket, Long operatorUserId);

    /** Every criterion that applied at least once — most often broken first. */
    List<FailedCriterion> findFailedCriteria(long companyId, Instant from, Instant to, Long operatorUserId);

    /** The {@code limit} tags on the most calls. */
    List<TopicCount> findTopicCounts(long companyId, Instant from, Instant to, Long operatorUserId, int limit);
}
