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
    public ResponseEntity<ResponseData<BillingOverviewResponse>> getOverview(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.overview(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<List<SpendMonthDto>>> getSpendChart(long companyId, int months) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.spendChart(companyId, months)));
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<InvoiceDto>>> getInvoices(long companyId, int page, int size) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.invoices(companyId, page, size)));
    }

    @Override
    public ResponseEntity<byte[]> downloadInvoicePdf(long companyId, String id) {
        byte[] pdfBytes = billingUseCase.invoicePdf(companyId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .body(pdfBytes);
    }

    @Override
    public ResponseEntity<ResponseData<TopupResponse>> topup(long companyId, TopupRequest request) {
        return ResponseEntity.ok(ResponseData.ok(billingUseCase.topup(companyId, request)));
    }
}
