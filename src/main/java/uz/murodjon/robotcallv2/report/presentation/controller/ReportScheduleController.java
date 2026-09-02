package uz.murodjon.robotcallv2.report.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.application.dto.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RequestMapping("/api/reports/schedule")
public interface ReportScheduleController {

    @PostMapping
    ResponseEntity<ResponseData<ReportSchedule>> create(@Valid @RequestBody CreateReportScheduleRequest request);

    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<ReportSchedule>>> list(@Valid @RequestBody ReportScheduleFilter filter);

    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<ReportSchedule>> disable(@PathVariable long id);
}
