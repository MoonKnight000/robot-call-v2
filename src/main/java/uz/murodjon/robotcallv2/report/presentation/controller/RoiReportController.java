package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uz.murodjon.robotcallv2.report.application.dto.RoiReportResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The ROI report (docs/api/reports-roi.md): what the robot did and cost against what operators
 * would have cost for the same calls.
 */
@RequestMapping("/api/reports")
public interface RoiReportController {

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/roi")
    ResponseEntity<ResponseData<RoiReportResponse>> roi(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Long operatorHourlyCostUzs);
}
