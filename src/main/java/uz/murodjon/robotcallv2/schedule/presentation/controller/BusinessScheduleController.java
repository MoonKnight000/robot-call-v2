package uz.murodjon.robotcallv2.schedule.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveBusinessScheduleRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.SaveHolidayRequest;
import uz.murodjon.robotcallv2.schedule.application.dto.ScheduleStatusResponse;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessSchedule;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessScheduleFilter;
import uz.murodjon.robotcallv2.schedule.domain.entity.Holiday;
import uz.murodjon.robotcallv2.schedule.domain.entity.HolidayFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Business schedules and holidays (docs/api/schedules.md). Both belong to the call-flow
 * settings: a schedule is what a flow's SCHEDULE node and a route's hours read.
 */
@RequestMapping("/api")
public interface BusinessScheduleController {

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PostMapping("/schedules")
    ResponseEntity<ResponseData<BusinessSchedule>> create(@CurrentCompanyId long companyId,
                                                          @Valid @RequestBody SaveBusinessScheduleRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @PostMapping("/schedules/list")
    ResponseEntity<ResponseData<PageableData<BusinessSchedule>>> list(@CurrentCompanyId long companyId,
                                                                      @Valid @RequestBody BusinessScheduleFilter filter);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @GetMapping("/schedules/{id}")
    ResponseEntity<ResponseData<BusinessSchedule>> get(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PutMapping("/schedules/{id}")
    ResponseEntity<ResponseData<BusinessSchedule>> update(@CurrentCompanyId long companyId, @PathVariable long id,
                                                          @Valid @RequestBody SaveBusinessScheduleRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @DeleteMapping("/schedules/{id}")
    ResponseEntity<ResponseData<Void>> delete(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @GetMapping("/schedules/{id}/status")
    ResponseEntity<ResponseData<ScheduleStatusResponse>> status(@CurrentCompanyId long companyId, @PathVariable long id);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PostMapping("/holidays")
    ResponseEntity<ResponseData<Holiday>> createHoliday(@CurrentCompanyId long companyId,
                                                        @Valid @RequestBody SaveHolidayRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_READ')")
    @PostMapping("/holidays/list")
    ResponseEntity<ResponseData<PageableData<Holiday>>> listHolidays(@CurrentCompanyId long companyId,
                                                                     @Valid @RequestBody HolidayFilter filter);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PutMapping("/holidays/{id}")
    ResponseEntity<ResponseData<Holiday>> updateHoliday(@CurrentCompanyId long companyId, @PathVariable long id,
                                                        @Valid @RequestBody SaveHolidayRequest request);

    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @DeleteMapping("/holidays/{id}")
    ResponseEntity<ResponseData<Void>> deleteHoliday(@CurrentCompanyId long companyId, @PathVariable long id);

    /** Adds the year's fixed-date public holidays (Navro'z, Mustaqillik kuni, ...) not yet on the calendar. */
    @PreAuthorize("hasAuthority('CALL_FLOW_EDIT')")
    @PostMapping("/holidays/fixed")
    ResponseEntity<ResponseData<List<Holiday>>> createFixedHolidays(@CurrentCompanyId long companyId,
                                                                    @RequestParam int year);
}
