package uz.murodjon.robotcallv2.billing.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingUsageRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CompanyBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.InvoiceRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.PaymentTopupRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingUsage;
import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;
import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.company.application.port.output.CompanyRepository;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private CompanyBillingRepository billingRepo;

    @Mock
    private BillingUsageRepository usageRepo;

    @Mock
    private InvoiceRepository invoiceRepo;

    @Mock
    private PaymentTopupRepository topupRepo;

    @Mock
    private CurrentCompany currentCompany;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private CompanyRepository companyRepo;

    @Mock
    private InvoicePdfService pdfService;

    @Mock
    private AuditService audit;

    private BillingService service;

    @BeforeEach
    void setUp() {
        service = new BillingService(
                billingRepo,
                usageRepo,
                invoiceRepo,
                topupRepo,
                currentCompany,
                currentUser,
                companyRepo,
                pdfService,
                audit
        );
        when(currentCompany.id()).thenReturn(1L);
    }

    @Test
    @DisplayName("overview returns billing overview and active usage metrics")
    void overviewSuccess() {
        CompanyBilling billing = CompanyBilling.defaultFor(1L);
        BillingUsage usage = BillingUsage.defaultFor(1L, "2026-03");

        when(billingRepo.findByCompanyId(1L)).thenReturn(Optional.of(billing));
        when(usageRepo.findByCompanyIdAndPeriod(eq(1L), anyString())).thenReturn(Optional.of(usage));

        BillingOverviewResponse response = service.overview();

        assertThat(response.planName()).isEqualTo("Professional (Pro)");
        assertThat(response.planCode()).isEqualTo("PRO_MONTHLY");
        assertThat(response.balanceUzs()).isEqualTo(1450000L);
        assertThat(response.metrics().minutes().used()).isEqualTo(3420);
        assertThat(response.metrics().minutes().limit()).isEqualTo(5000);
    }

    @Test
    @DisplayName("spendChart returns recent spend months")
    void spendChartSuccess() {
        BillingUsage u1 = BillingUsage.defaultFor(1L, "2026-02");
        BillingUsage u2 = BillingUsage.defaultFor(1L, "2026-03");
        when(usageRepo.findRecentByCompanyId(1L, 6)).thenReturn(List.of(u1, u2));

        List<SpendMonthDto> chart = service.spendChart(6);

        assertThat(chart).hasSize(2);
        assertThat(chart.get(0).month()).isEqualTo("2026-02");
        assertThat(chart.get(1).month()).isEqualTo("2026-03");
    }

    @Test
    @DisplayName("invoices returns paginated invoice list with pdf urls")
    void invoicesSuccess() {
        Invoice inv = new Invoice("INV-001", 1L, "Mart 2026", 1450000L, InvoiceStatus.PAID, Instant.now(), null, Instant.now());
        when(invoiceRepo.findAllByCompanyId(1L, 0, 10)).thenReturn(new PageableData<>(1, 0, 1, List.of(inv)));

        PageableData<InvoiceDto> result = service.invoices(0, 10);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).id()).isEqualTo("INV-001");
        assertThat(result.data().get(0).pdfUrl()).isEqualTo("/api/billing/invoices/INV-001/pdf");
    }

    @Test
    @DisplayName("topup creates payment record and checkout url")
    void topupSuccess() {
        when(currentUser.id()).thenReturn(Optional.of(10L));
        TopupRequest request = new TopupRequest(500000L, PaymentMethod.PAYME);

        TopupResponse response = service.topup(request);

        assertThat(response.paymentId()).startsWith("PAY-");
        assertThat(response.checkoutUrl()).contains("checkout.paycom.uz");
        verify(topupRepo).save(any());
        verify(audit).record(eq("BILLING_TOPUP_INITIATED"), eq("company"), eq("1"), anyString());
    }

    @Test
    @DisplayName("invoicePdf generates pdf bytes for company invoice")
    void invoicePdfSuccess() {
        Invoice inv = new Invoice("INV-001", 1L, "Mart 2026", 1450000L, InvoiceStatus.PAID, Instant.now(), null, Instant.now());
        when(invoiceRepo.findById("INV-001")).thenReturn(Optional.of(inv));
        when(companyRepo.find(1L)).thenReturn(new Company(1L, "Acme Corp", CompanyStatus.ACTIVE, Instant.now(), null, "Tashkent"));
        when(pdfService.generateInvoicePdf(eq(inv), eq("Acme Corp"))).thenReturn(new byte[]{1, 2, 3});

        byte[] bytes = service.invoicePdf("INV-001");

        assertThat(bytes).containsExactly(1, 2, 3);
    }
}
