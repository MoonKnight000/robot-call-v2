package uz.murodjon.robotcallv2.report.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.dto.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RequestMapping("/api/reports/schedule")
public interface ReportScheduleController {

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<ReportSchedule>> create(@Valid @RequestBody CreateReportScheduleRequest request);

    @PreAuthorize("hasAuthority('REPORT_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<ReportSchedule>>> list(@Valid @RequestBody ReportScheduleFilter filter);

    @PreAuthorize("hasAuthority('REPORT_EDIT')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<ReportSchedule>> disable(@PathVariable long id);
}
