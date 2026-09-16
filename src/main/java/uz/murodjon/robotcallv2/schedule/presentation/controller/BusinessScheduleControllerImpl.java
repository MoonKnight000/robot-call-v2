package uz.murodjon.robotcallv2.schedule.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveBusinessScheduleRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveHolidayRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.ScheduleStatusResponse;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessSchedule;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleFilter;
import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.domain.entity.HolidayFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class BusinessScheduleControllerImpl implements BusinessScheduleController {

    private final BusinessScheduleUseCase businessScheduleUseCase;

    public BusinessScheduleControllerImpl(BusinessScheduleUseCase businessScheduleUseCase) {
        this.businessScheduleUseCase = businessScheduleUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<BusinessSchedule>> create(long companyId, SaveBusinessScheduleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.create(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<BusinessSchedule>>> list(long companyId, BusinessScheduleFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.list(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<BusinessSchedule>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.requireSchedule(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<BusinessSchedule>> update(long companyId, long id, SaveBusinessScheduleRequest request) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.update(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long id) {
        businessScheduleUseCase.delete(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<ScheduleStatusResponse>> status(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.findStatus(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<Holiday>> createHoliday(long companyId, SaveHolidayRequest request) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.createHoliday(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<Holiday>>> listHolidays(long companyId, HolidayFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.listHolidays(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<Holiday>> updateHoliday(long companyId, long id, SaveHolidayRequest request) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.updateHoliday(companyId, id, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> deleteHoliday(long companyId, long id) {
        businessScheduleUseCase.deleteHoliday(companyId, id);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<ResponseData<List<Holiday>>> createFixedHolidays(long companyId, int year) {
        return ResponseEntity.ok(ResponseData.ok(businessScheduleUseCase.createFixedHolidays(companyId, year)));
    }
}
