package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallEvent;
import uz.murodjon.robotcallv2.report.application.dto.OperatorReportRow;
import uz.murodjon.robotcallv2.report.application.dto.QueueReportRow;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/** Queue and operator reports and a call's timeline (docs/api/reports.md, "Kontakt-markaz"). */
@RequestMapping("/api/reports")
public interface ContactCenterReportController {

    /** SLA, ASA, abandon rate, overflow and survey score per queue; {@code from}/{@code to} as on the dashboard. */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/queues")
    ResponseEntity<ResponseData<List<QueueReportRow>>> queues(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to);

    /** Answered, missed (RONA), talk time, time in status and occupancy per operator. */
    @PreAuthorize("hasAuthority('REPORT_READ')")
    @GetMapping("/operators")
    ResponseEntity<ResponseData<List<OperatorReportRow>>> operators(
            @CurrentCompanyId long companyId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to);

    @PreAuthorize("hasAuthority('CALL_READ')")
    @GetMapping("/calls/{callId}/events")
    ResponseEntity<ResponseData<List<CallEvent>>> callEvents(
            @CurrentCompanyId long companyId,
            @PathVariable long callId);
}
