package uz.murodjon.robotcallv2.billing.presentation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.application.port.input.BillingUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;

@RestController
public class BillingControllerImpl implements BillingController {

    private final BillingUseCase billingUseCase;

    public BillingControllerImpl(BillingUseCase billingUseCase) {
        this.billingUseCase = billingUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<BillingOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.overview()));
    }

    @Override
    public ResponseEntity<ResponseData<List<SpendMonthDto>>> getSpendChart(int months) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.spendChart(months)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<InvoiceDto>>> getInvoices(int page, int size) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.invoices(page, size)));
    }

    @Override
    public ResponseEntity<byte[]> downloadInvoicePdf(String id) {
        byte[] pdfBytes = billingUseCase.invoicePdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .body(pdfBytes);
    }

    @Override
    public ResponseEntity<ResponseData<TopupResponse>> topup(TopupRequest request) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.topup(request)));
    }
}
