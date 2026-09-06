package uz.murodjon.robotcallv2.report.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import uz.murodjon.robotcallv2.report.application.dto.CreateReportScheduleRequest;
import uz.murodjon.robotcallv2.report.domain.entity.ReportScheduleFilter;
import uz.murodjon.robotcallv2.report.application.port.input.ReportScheduleUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.ReportSchedule;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ReportScheduleControllerImpl implements ReportScheduleController {

    private final ReportScheduleUseCase scheduleService;

    public ReportScheduleControllerImpl(ReportScheduleUseCase scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Override
    public ResponseEntity<ResponseData<ReportSchedule>> create(long companyId, CreateReportScheduleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(scheduleService.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ReportSchedule>>> list(long companyId, ReportScheduleFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(scheduleService.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ReportSchedule>> disable(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(scheduleService.disable(companyId, id)));
    }
}
