package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface BillingUseCase {

    BillingOverviewResponse overview();

    List<SpendMonthDto> spendChart(int months);

    PageableData<InvoiceDto> invoices(int page, int size);

    byte[] invoicePdf(String invoiceId);

    TopupResponse topup(TopupRequest request);
}
