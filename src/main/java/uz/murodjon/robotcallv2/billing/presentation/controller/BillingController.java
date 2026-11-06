package uz.murodjon.robotcallv2.billing.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.time.LocalDate;
import java.util.List;

/**
 * Billing & Payments API (docs/api/billing.md).
 */
@RequestMapping("/api/billing")
public interface BillingController {

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/overview")
    ResponseEntity<ResponseData<BillingOverviewResponse>> getOverview(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/spend-chart")
    ResponseEntity<ResponseData<List<SpendMonthDto>>> getSpendChart(
            @CurrentCompanyId long companyId,
            @RequestParam(name = "months", defaultValue = "6") int months);

    /**
     * Everything spent in a period of days, split into its cost lines: tokens (input / output /
     * cached), recognition, synthesis, realtime, line, platform fee, and the token charges
     * outside calls (chat, copilot, questions, re-analysis). Defaults to this month so far.
     */
    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/costs")
    ResponseEntity<ResponseData<CostReportResponse>> getCostReport(
            @CurrentCompanyId long companyId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/invoices")
    ResponseEntity<ResponseData<PageableData<InvoiceDto>>> getInvoices(
            @CurrentCompanyId long companyId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size);

    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping(value = "/invoices/{id}/pdf", produces = "application/pdf")
    ResponseEntity<byte[]> downloadInvoicePdf(
            @CurrentCompanyId long companyId,
            @PathVariable("id") String id);

    /** The free trial of a self-registered company; data is null when it never had one. */
    @PreAuthorize("hasAuthority('BILLING_READ')")
    @GetMapping("/trial")
    ResponseEntity<ResponseData<TrialCreditResponse>> getTrial(@CurrentCompanyId long companyId);

    @PreAuthorize("hasAuthority('BILLING_EDIT')")
    @PostMapping("/topup")
    ResponseEntity<ResponseData<TopupResponse>> topup(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody TopupRequest request);
}
