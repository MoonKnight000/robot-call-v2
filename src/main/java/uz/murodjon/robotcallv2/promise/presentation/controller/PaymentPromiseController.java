package uz.murodjon.robotcallv2.promise.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseSettingsRequest;
import uz.murodjon.robotcallv2.promise.application.dto.UpdatePaymentPromiseStatusRequest;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromise;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseFilter;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseSettings;
import uz.murodjon.robotcallv2.promise.domain.entity.PaymentPromiseStats;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.time.LocalDate;

/**
 * Promises to pay that calls recorded, from the call to kept or broken: the reminder SMS, the
 * payment a company's system reports, the call back after a broken one, and the kept rate.
 */
@RequestMapping("/api/payment-promises")
public interface PaymentPromiseController {

    @PreAuthorize("hasAuthority('CALL_READ')")
    @PostMapping("/filter")
    ResponseEntity<ResponseData<PageableData<PaymentPromise>>> filter(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody PaymentPromiseFilter filter);

    @PreAuthorize("hasAuthority('CALL_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<PaymentPromise>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    /** Promises dated {@code from}–{@code to} (inclusive, at most 366 days) by outcome, and the kept rate. */
    @PreAuthorize("hasAuthority('CALL_READ')")
    @GetMapping("/stats")
    ResponseEntity<ResponseData<PaymentPromiseStats>> stats(
            @CurrentCompanyId long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    /** Marks an open promise {@code KEPT}, {@code BROKEN} or {@code CANCELLED} by hand. */
    @PreAuthorize("hasAuthority('CALL_EDIT')")
    @PostMapping("/{id:\\d+}/status")
    ResponseEntity<ResponseData<PaymentPromise>> updateStatus(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody UpdatePaymentPromiseStatusRequest request);

    @PreAuthorize("hasAuthority('CALL_READ')")
    @GetMapping("/settings")
    ResponseEntity<ResponseData<PaymentPromiseSettings>> getSettings(
            @CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('CAMPAIGN_EDIT')")
    @PutMapping("/settings")
    ResponseEntity<ResponseData<PaymentPromiseSettings>> updateSettings(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody UpdatePaymentPromiseSettingsRequest request);
}
