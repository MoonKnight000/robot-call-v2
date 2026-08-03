package uz.murodjon.uysotvoice.report.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.uysotvoice.report.dto.CreateReportScheduleRequest;
import uz.murodjon.uysotvoice.report.dto.ReportSchedule;
import uz.murodjon.uysotvoice.report.dto.ReportScheduleFilter;
import uz.murodjon.uysotvoice.report.service.ReportScheduleService;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

@RestController
public class ReportScheduleControllerImpl implements ReportScheduleController {

    private final ReportScheduleService service;

    public ReportScheduleControllerImpl(ReportScheduleService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ResponseData<ReportSchedule>> create(CreateReportScheduleRequest r) {
        return ResponseEntity.ok(ResponseData.ok(service.create(r)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ReportSchedule>>> list(ReportScheduleFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(service.list(filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ReportSchedule>> disable(long id) {
        return ResponseEntity.ok(ResponseData.ok(service.disable(id)));
    }
}
