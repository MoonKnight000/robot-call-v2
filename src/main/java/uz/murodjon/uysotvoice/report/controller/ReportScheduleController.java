package uz.murodjon.uysotvoice.report.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.report.dto.CreateReportScheduleRequest;
import uz.murodjon.uysotvoice.report.dto.ReportSchedule;
import uz.murodjon.uysotvoice.report.dto.ReportScheduleFilter;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * "📅 Jadval bo'yicha yuborish" (§10.10) — recurring report emails. Kept apart from
 * {@link ReportController} (read-only, viewer-key accessible): creating and cancelling
 * a schedule are writes, and the SMTP address it emails to is itself a piece of data
 * worth an admin-level key to change.
 */
@RequestMapping("/api/reports/schedule")
public interface ReportScheduleController {

    @PostMapping
    ResponseEntity<ResponseData<ReportSchedule>> create(@Valid @RequestBody CreateReportScheduleRequest r);

    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<ReportSchedule>>> list(@Valid @RequestBody ReportScheduleFilter filter);

    /** Cancel a schedule (soft-delete, mirrors campaign archive / inbound-route disable). */
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<ReportSchedule>> disable(@PathVariable long id);
}
