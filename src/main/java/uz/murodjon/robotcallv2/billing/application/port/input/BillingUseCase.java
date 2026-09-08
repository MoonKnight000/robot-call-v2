package uz.murodjon.robotcallv2.billing.application.port.input;

import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.domain.entity.VariantSpend;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;

public interface BillingUseCase {

    BillingOverviewResponse overview(long companyId);

    List<SpendMonthDto> spendChart(long companyId, int months);

    PageableData<InvoiceDto> invoices(long companyId, int page, int size);

    byte[] invoicePdf(long companyId, String invoiceId);

    TopupResponse topup(long companyId, TopupRequest request);

    /**
     * What each A/B variant of a campaign has cost, for the A/B report.
     *
     * <p>A variant that converts slightly better while talking twice as long is not the
     * better variant, and that only shows once the charges are split this way.
     */
    List<VariantSpend> findVariantSpend(long companyId, long campaignId);
}
