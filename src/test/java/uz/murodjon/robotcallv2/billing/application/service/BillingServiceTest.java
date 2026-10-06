package uz.murodjon.robotcallv2.billing.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.application.port.output.*;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentClickProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentPaymeProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.security.CurrentUser;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    /** Payme configured with made-up test values, Click left off. */
    private static final PaymentProperties PAYMENTS = new PaymentProperties(Duration.ofHours(24), null,
            new PaymentPaymeProperties(true, "test-merchant", "test-key", null, null, null, null, 12),
            new PaymentClickProperties(false, null, null, null, null));

    @Mock
    private CompanyBillingRepository billingRepo;

    @Mock
    private BillingUsageRepository usageRepo;

    @Mock
    private CallBillingRepository callBillingRepo;

    @Mock
    private InvoiceRepository invoiceRepo;

    @Mock
    private PaymentTopupRepository topupRepo;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private CompanyUseCase companyUseCase;

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
                callBillingRepo,
                invoiceRepo,
                topupRepo,
                currentUser,
                companyUseCase,
                pdfService,
                audit,
                PAYMENTS,
                Clock.systemUTC()
        );
    }

    /**
     * The allowances come from the plan row, what has been used from the calls the company
     * was charged for. The two used to come from the same row, which is how every company
     * ended up being shown the same invented usage.
     */
    @Test
    @DisplayName("overview measures usage from settled calls, not from the plan row")
    void overviewSuccess() {
        when(billingRepo.findByCompanyId(1L)).thenReturn(Optional.of(CompanyBilling.defaultFor(1L)));
        when(usageRepo.findByCompanyIdAndPeriod(eq(1L), anyString()))
                .thenReturn(Optional.of(BillingUsage.defaultFor(1L, "2026-09")));
        // 7200s = 120 minutes charged this month.
        when(callBillingRepo.findUsageSince(eq(1L), any()))
                .thenReturn(new PeriodUsage(7200, 45_000, 12_000, 380_000));

        BillingOverviewResponse response = service.findOverview(1L);

        assertThat(response.planName()).isEqualTo("Professional (Pro)");
        assertThat(response.balanceUzs()).isZero();
        assertThat(response.metrics().minutes().used()).isEqualTo(120);
        assertThat(response.metrics().minutes().limit()).isEqualTo(5000);
        assertThat(response.metrics().aiTokens().used()).isEqualTo(45_000);
    }

    @Test
    @DisplayName("spendChart returns charged months oldest first")
    void spendChartSuccess() {
        when(callBillingRepo.findMonthlySpend(1L, 6)).thenReturn(List.of(
                new MonthlySpend("2026-09", 380_000, 7200),
                new MonthlySpend("2026-08", 120_000, 3600)));

        List<SpendMonthDto> chart = service.findSpendChart(1L, 6);

        assertThat(chart).hasSize(2);
        assertThat(chart.get(0).month()).isEqualTo("2026-08");
        assertThat(chart.get(1).month()).isEqualTo("2026-09");
        assertThat(chart.get(1).callMinutes()).isEqualTo(120);
    }

    /** A company with no calls yet gets an empty chart, not a fabricated one. */
    @Test
    @DisplayName("spendChart is empty when nothing has been charged")
    void spendChartEmpty() {
        when(callBillingRepo.findMonthlySpend(1L, 6)).thenReturn(List.of());

        assertThat(service.findSpendChart(1L, 6)).isEmpty();
    }

    @Test
    @DisplayName("invoices returns paginated invoice list with pdf urls")
    void invoicesSuccess() {
        Invoice inv = new Invoice("INV-001", 1L, "Mart 2026", 1450000L, InvoiceStatus.PAID, Instant.now(), null, Instant.now());
        when(invoiceRepo.findAllByCompanyId(1L, 0, 10)).thenReturn(new PageableData<>(1, 0, 1, List.of(inv)));

        PageableData<InvoiceDto> result = service.findInvoices(1L, 0, 10);

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).id()).isEqualTo("INV-001");
        assertThat(result.data().get(0).pdfUrl()).isEqualTo("/api/billing/invoices/INV-001/pdf");
    }

    @Test
    @DisplayName("topup creates payment record and checkout url")
    void topupSuccess() {
        when(currentUser.id()).thenReturn(Optional.of(10L));
        TopupRequest request = new TopupRequest(500000L, PaymentMethod.PAYME);

        TopupResponse response = service.createTopup(1L, request);

        assertThat(response.paymentId()).startsWith("PAY-");
        assertThat(response.checkoutUrl()).startsWith("https://checkout.paycom.uz/");
        verify(topupRepo).save(any());
        verify(audit).record(eq(1L), eq("BILLING_TOPUP_INITIATED"), eq("company"), eq("1"), anyString());
    }

    /** It used to hand out a link to a made-up merchant; now the payer is told before paying anyone. */
    @Test
    @DisplayName("topup through an unconfigured provider is refused and nothing is saved")
    void topupRefusedWhenProviderNotConfigured() {
        TopupRequest request = new TopupRequest(500000L, PaymentMethod.CLICK);

        assertThatThrownBy(() -> service.createTopup(1L, request))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYMENT_METHOD_NOT_CONFIGURED));
        verify(topupRepo, never()).save(any());
    }

    @Test
    @DisplayName("invoicePdf generates pdf bytes for company invoice")
    void invoicePdfSuccess() {
        Invoice inv = new Invoice("INV-001", 1L, "Mart 2026", 1450000L, InvoiceStatus.PAID, Instant.now(), null, Instant.now());
        when(invoiceRepo.findById("INV-001")).thenReturn(Optional.of(inv));
        when(companyUseCase.findById(1L)).thenReturn(new Company(1L, "Acme Corp", CompanyStatus.ACTIVE, Instant.now(), null, "Tashkent"));
        when(pdfService.generateInvoicePdf(eq(inv), eq("Acme Corp"))).thenReturn(new byte[]{1, 2, 3});

        byte[] bytes = service.renderInvoicePdf(1L, "INV-001");

        assertThat(bytes).containsExactly(1, 2, 3);
    }

    /** Another company's invoice is "not found" — a 403 used to confirm that the id exists. */
    @Test
    void invoicePdfOfAnotherCompanyIsNotFound() {
        Invoice inv = new Invoice("INV-002", 2L, "Mart 2026", 1450000L, InvoiceStatus.PAID, Instant.now(), null, Instant.now());
        when(invoiceRepo.findById("INV-002")).thenReturn(Optional.of(inv));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.renderInvoicePdf(1L, "INV-002"))
                .isInstanceOf(uz.murodjon.robotcallv2.shared.exception.NotFoundException.class);
    }
}
