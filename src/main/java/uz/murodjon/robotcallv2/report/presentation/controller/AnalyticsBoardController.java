package uz.murodjon.robotcallv2.report.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.report.application.dto.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * REST API for custom analytics boards and ad-hoc aggregations.
 */
@RequestMapping("/api/reports/boards")
public interface AnalyticsBoardController {

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<BoardDetail>> create(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody CreateBoardRequest request);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping
    ResponseEntity<ResponseData<List<BoardRow>>> list(
            @CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/{id}")
    ResponseEntity<ResponseData<BoardDetail>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PutMapping("/{id}")
    ResponseEntity<ResponseData<BoardDetail>> update(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdateBoardRequest request);

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> delete(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping("/{id}/widgets")
    ResponseEntity<ResponseData<BoardWidgetRow>> addWidget(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody AddBoardWidgetRequest request);

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @DeleteMapping("/{id}/widgets/{widgetId}")
    ResponseEntity<ResponseData<Void>> deleteWidget(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @PathVariable long widgetId);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @PostMapping("/query")
    ResponseEntity<ResponseData<AnalyticsQueryResult>> query(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody AnalyticsQueryRequest request);
}
