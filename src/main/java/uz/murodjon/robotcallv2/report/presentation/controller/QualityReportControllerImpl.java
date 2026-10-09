package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.report.application.port.input.QualityReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.CriterionTrendPoint;
import uz.murodjon.robotcallv2.report.domain.entity.FailedCriterion;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorQualityScore;
import uz.murodjon.robotcallv2.report.domain.entity.TopicCount;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class QualityReportControllerImpl implements QualityReportController {

    private final QualityReportUseCase qualityReportUseCase;

    public QualityReportControllerImpl(QualityReportUseCase qualityReportUseCase) {
        this.qualityReportUseCase = qualityReportUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<OperatorQualityScore>>> operators(long companyId, String from, String to) {
        return ResponseEntity.ok(ResponseData.ok(qualityReportUseCase.findOperatorRating(companyId, from, to)));
    }

    @Override
    public ResponseEntity<ResponseData<List<CriterionTrendPoint>>> criteriaTrend(long companyId, String from, String to,
                                                                               TrendBucket bucket,
                                                                               Long operatorUserId) {
        return ResponseEntity.ok(ResponseData.ok(
                qualityReportUseCase.findCriterionTrend(companyId, from, to, bucket, operatorUserId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<FailedCriterion>>> failedCriteria(long companyId, String from, String to,
                                                                            Long operatorUserId) {
        return ResponseEntity.ok(ResponseData.ok(
                qualityReportUseCase.findFailedCriteria(companyId, from, to, operatorUserId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<TopicCount>>> topics(long companyId, String from, String to,
                                                                Long operatorUserId, Integer limit) {
        return ResponseEntity.ok(ResponseData.ok(
                qualityReportUseCase.findTopics(companyId, from, to, operatorUserId, limit)));
    }
}
