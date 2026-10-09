package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uz.murodjon.robotcallv2.report.domain.entity.CriterionTrendPoint;
import uz.murodjon.robotcallv2.report.domain.entity.FailedCriterion;
import uz.murodjon.robotcallv2.report.domain.entity.OperatorQualityScore;
import uz.murodjon.robotcallv2.report.domain.entity.TopicCount;
import uz.murodjon.robotcallv2.report.domain.enums.TrendBucket;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Operator call quality reports (docs/api/reports.md, "Operator qo'ng'iroqlari sifati"), read from
 * the scorecards; {@code from}/{@code to} as in the contact-centre reports.
 */
@RequestMapping("/api/reports/quality")
public interface QualityReportController {

    /** Every operator: scored calls, average score, flagged, corrected, upset clients, talk metrics. */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/operators")
    ResponseEntity<ResponseData<List<OperatorQualityScore>>> operators(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to);

    /** Each criterion's pass rate per day or week. */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/criteria-trend")
    ResponseEntity<ResponseData<List<CriterionTrendPoint>>> criteriaTrend(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) TrendBucket bucket,
            @RequestParam(required = false) Long operatorUserId);

    /** The criteria broken most often. */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/failed-criteria")
    ResponseEntity<ResponseData<List<FailedCriterion>>> failedCriteria(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long operatorUserId);

    /** What the calls were about, most frequent first. */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/topics")
    ResponseEntity<ResponseData<List<TopicCount>>> topics(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long operatorUserId,
            @RequestParam(required = false) Integer limit);
}
