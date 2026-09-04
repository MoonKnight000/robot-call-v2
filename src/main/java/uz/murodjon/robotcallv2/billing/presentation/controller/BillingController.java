package uz.murodjon.robotcallv2.billing.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RequestMapping("/api/billing")
public interface BillingController {

    @GetMapping("/overview")
    ResponseEntity<ResponseData<BillingOverviewResponse>> getOverview();

    @GetMapping("/spend-chart")
    ResponseEntity<ResponseData<List<SpendMonthDto>>> getSpendChart(
            @RequestParam(name = "months", defaultValue = "6") int months
    );

    @GetMapping("/invoices")
    ResponseEntity<ResponseData<PageableData<InvoiceDto>>> getInvoices(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    );

    @GetMapping(value = "/invoices/{id}/pdf", produces = "application/pdf")
    ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable("id") String id);

    @PostMapping("/topup")
    ResponseEntity<ResponseData<TopupResponse>> topup(@Valid @RequestBody TopupRequest request);
}
