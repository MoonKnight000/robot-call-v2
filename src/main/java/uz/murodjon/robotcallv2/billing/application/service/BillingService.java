package uz.murodjon.robotcallv2.billing.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.application.port.input.BillingUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.*;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;
import uz.murodjon.robotcallv2.billing.domain.service.PaymentCheckoutUrls;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentClickProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentPaymeProperties;
import uz.murodjon.robotcallv2.billing.infrastructure.config.PaymentProperties;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService implements BillingUseCase {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingUsageRepository billingUsageRepository;
    private final CallBillingRepository callBillingRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentTopupRepository paymentTopupRepository;
    private final CurrentUser currentUser;
    private final CompanyUseCase companyUseCase;
    private final InvoicePdfService pdfService;
    private final AuditUseCase audit;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    public BillingService(
            CompanyBillingRepository companyBillingRepository,
            BillingUsageRepository billingUsageRepository,
            CallBillingRepository callBillingRepository,
            InvoiceRepository invoiceRepository,
            PaymentTopupRepository paymentTopupRepository,
            CurrentUser currentUser,
            CompanyUseCase companyUseCase,
            InvoicePdfService pdfService,
            AuditUseCase audit,
            PaymentProperties paymentProperties,
            Clock clock
    ) {
        this.companyBillingRepository = companyBillingRepository;
        this.billingUsageRepository = billingUsageRepository;
        this.callBillingRepository = callBillingRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentTopupRepository = paymentTopupRepository;
        this.currentUser = currentUser;
        this.companyUseCase = companyUseCase;
        this.pdfService = pdfService;
        this.audit = audit;
        this.paymentProperties = paymentProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BillingOverviewResponse findOverview(long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseGet(() -> companyBillingRepository.save(CompanyBilling.defaultFor(companyId)));

        String currentPeriod = DateTimeFormatter.ofPattern("yyyy-MM")
                .withZone(ZoneId.of("Asia/Tashkent"))
                .format(clock.instant());

        BillingUsage limits = billingUsageRepository.findByCompanyIdAndPeriod(companyId, currentPeriod)
                .orElseGet(() -> billingUsageRepository.save(BillingUsage.defaultFor(companyId, currentPeriod)));

        // The allowances come from the plan row; what has been used against them is summed
        // from the calls the company was actually charged for.
        PeriodUsage used = callBillingRepository.findUsageSince(companyId, startOfMonth());

        BillingMetricsDto metrics = new BillingMetricsDto(
                new BillingMetricItemDto(used.minutes(), limits.limitMinutes(), "daqiqa", limits.overagePriceMinute()),
                new BillingMetricItemDto(used.totalTokens(), limits.limitTokens(), "token", limits.overagePriceToken()),
                new BillingMetricItemDto(used.ttsChars(), limits.limitTtsChars(), "belgi", limits.overagePriceTts()),
                new BillingMetricItemDto(limits.usedChannels(), limits.limitChannels(), "kanal", null)
        );

        return new BillingOverviewResponse(
                billing.planName(),
                billing.planCode(),
                billing.balanceUzs(),
                billing.nextBillingDate(),
                billing.autoRecharge(),
                metrics
        );
    }

    @Override
    public List<SpendMonthDto> findSpendChart(long companyId, int months) {
        int limit = months <= 0 ? 6 : Math.min(months, 24);
        // Oldest month first: this is a chart, and a company with no calls yet gets an
        // empty one rather than the invented months it used to be shown.
        return callBillingRepository.findMonthlySpend(companyId, limit).reversed().stream()
                .map(month -> new SpendMonthDto(month.period(), month.spendUzs(),
                        (int) ((month.durationSec() + 59) / 60)))
                .toList();
    }

    @Override
    public PageableData<InvoiceDto> findInvoices(long companyId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        PageableData<Invoice> pageResult = invoiceRepository.findAllByCompanyId(companyId, safePage, safeSize);
        List<InvoiceDto> dtoList = pageResult.data().stream()
                .map(this::toInvoiceDto)
                .toList();

        return new PageableData<>(pageResult.totalPages(), pageResult.currentPage(), pageResult.totalElements(), dtoList);
    }

    @Override
    public byte[] renderInvoicePdf(long companyId, String invoiceId) {
        // Another company's invoice is "not found", not "forbidden": a 403 confirmed the id exists.
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .filter(found -> found.companyId() == companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND, invoiceId));

        Company company = companyUseCase.findById(companyId);
        String companyName = company != null ? company.name() : null;

        return pdfService.generateInvoicePdf(invoice, companyName);
    }

    @Override
    @Transactional
    public TopupResponse createTopup(long companyId, TopupRequest request) {
        Long userId = currentUser.id().orElse(null);

        String paymentId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String checkoutUrl = buildCheckoutUrl(request.paymentMethod(), paymentId, request.amountUzs());

        PaymentTopup topup = new PaymentTopup(
                null,
                paymentId,
                companyId,
                userId,
                request.amountUzs(),
                request.paymentMethod(),
                TopupStatus.PENDING,
                checkoutUrl,
                clock.instant(),
                null
        );

        paymentTopupRepository.save(topup);
        audit.record(companyId, "BILLING_TOPUP_INITIATED", "company", String.valueOf(companyId),
                "Amount: " + request.amountUzs() + " UZS, Method: " + request.paymentMethod());

        return new TopupResponse(paymentId, checkoutUrl);
    }

    /** The start of the current billing month, in the timezone the periods are keyed on. */
    @Override
    @Transactional(readOnly = true)
    public List<VariantSpend> findVariantSpend(long companyId, long campaignId) {
        return callBillingRepository.findSpendByVariant(companyId, campaignId);
    }

    private Instant startOfMonth() {
        ZoneId zone = ZoneId.of("Asia/Tashkent");
        return LocalDate.ofInstant(clock.instant(), zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
    }

    private InvoiceDto toInvoiceDto(Invoice inv) {
        return new InvoiceDto(
                inv.id(),
                inv.periodName(),
                inv.amountUzs(),
                inv.status(),
                inv.paidAt(),
                "/api/billing/invoices/" + inv.id() + "/pdf"
        );
    }

    /**
     * Where the payer goes to pay. A provider that is not configured refuses the top-up
     * outright: a link to a made-up merchant used to be handed out instead, and the payer
     * found out only on the provider's page.
     */
    private String buildCheckoutUrl(PaymentMethod method, String paymentId, long amountUzs) {
        return switch (method) {
            case PAYME -> {
                PaymentPaymeProperties payme = paymentProperties.payme();
                if (!payme.configured()) {
                    throw new ConflictException(ErrorCode.PAYMENT_METHOD_NOT_CONFIGURED, method);
                }
                yield PaymentCheckoutUrls.payme(payme.checkoutUrl(), payme.merchantId(), paymentId, amountUzs,
                        payme.returnUrl());
            }
            case CLICK -> {
                PaymentClickProperties click = paymentProperties.click();
                if (!click.configured()) {
                    throw new ConflictException(ErrorCode.PAYMENT_METHOD_NOT_CONFIGURED, method);
                }
                yield PaymentCheckoutUrls.click(click.serviceId(), click.merchantId(), paymentId, amountUzs,
                        click.returnUrl());
            }
            case BANK_TRANSFER -> "/api/billing/topup/" + paymentId + "/bank-details";
        };
    }
}
