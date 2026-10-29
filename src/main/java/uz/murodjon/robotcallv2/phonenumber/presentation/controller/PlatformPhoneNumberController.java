package uz.murodjon.robotcallv2.phonenumber.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.phonenumber.application.dto.AddPoolNumberRequest;
import uz.murodjon.robotcallv2.phonenumber.application.dto.PhoneNumberRow;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.PhoneNumberFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

/**
 * Platform admin operations for managing the phone number inventory pool (10-100+ numbers).
 */
@RequestMapping("/api/platform/phone-numbers")
public interface PlatformPhoneNumberController {

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping
    ResponseEntity<ResponseData<PhoneNumberRow>> addPoolNumber(
            @Valid @RequestBody AddPoolNumberRequest request);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/batch")
    ResponseEntity<ResponseData<Integer>> addBatchPoolNumbers(
            @Valid @RequestBody List<@Valid AddPoolNumberRequest> requests);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<PhoneNumberRow>>> findAllPoolNumbers(
            @Valid @RequestBody PhoneNumberFilter filter);

    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    ResponseEntity<ResponseData<Void>> removePoolNumber(
            @PathVariable long id);
}
