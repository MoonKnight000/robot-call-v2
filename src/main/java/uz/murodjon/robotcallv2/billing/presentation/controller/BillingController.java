package uz.murodjon.robotcallv2.billing.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RequestMapping("/api/billing")
public interface BillingController {

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/overview")
    ResponseEntity<ResponseData<BillingOverviewResponse>> getOverview(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/spend-chart")
    ResponseEntity<ResponseData<List<SpendMonthDto>>> getSpendChart(
            @CurrentCompanyId long companyId,
            @RequestParam(name = "months", defaultValue = "6") int months
    );

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/invoices")
    ResponseEntity<ResponseData<PageableData<InvoiceDto>>> getInvoices(
            @CurrentCompanyId long companyId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    );

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping(value = "/invoices/{id}/pdf", produces = "application/pdf")
    ResponseEntity<byte[]> downloadInvoicePdf(@CurrentCompanyId long companyId, @PathVariable("id") String id);

    @PreAuthorize("hasAuthority('BILLING_EDIT')")
    @PostMapping("/topup")
    ResponseEntity<ResponseData<TopupResponse>> topup(@CurrentCompanyId long companyId, @Valid @RequestBody TopupRequest request);
}
