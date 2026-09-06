package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface BillingUseCase {

    BillingOverviewResponse overview(long companyId);

    List<SpendMonthDto> spendChart(long companyId, int months);

    PageableData<InvoiceDto> invoices(long companyId, int page, int size);

    byte[] invoicePdf(long companyId, String invoiceId);

    TopupResponse topup(long companyId, TopupRequest request);
}
