package uz.murodjon.robotcallv2.billing.application.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.billing.application.dto.CostComponentDto;
import uz.murodjon.robotcallv2.billing.application.dto.CostReportResponse;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CallBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.LlmUsageRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.*;
import uz.murodjon.robotcallv2.billing.domain.enums.CostComponent;
import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingRateProperties;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillingCostServiceTest {

    private static final long COMPANY = 1L;
    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");

    private final CallBillingRepository callBilling = mock(CallBillingRepository.class);
    private final BillingLedgerRepository ledger = mock(BillingLedgerRepository.class);
    private final LlmUsageRepository llmUsage = mock(LlmUsageRepository.class);
    private final BillingRateProperties rates = new BillingRateProperties("v1", true, 5000,
            new BillingRates(new BillingLlmRates(1000, 4000, 250), 60, 50, 300, 180, 100, 2000), null);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T10:00:00Z"), TASHKENT);
    private final BillingCostService service = new BillingCostService(callBilling, ledger, llmUsage, rates, clock);

    /** The total is calls and the token charges outside calls together — the ledger-only ones used to be missing. */
    @Test
    void addsChargesOutsideCallsToTheTotal() {
        when(callBilling.findCostTotals(eq(COMPANY), any(), any())).thenReturn(new CostTotals(
                3, 300, 300, 0, 10_000, 2_000, 5_000, 900,
                40, 30, 5, 75, 360, 54, 0, 1080, 500, 2069));
        when(ledger.findExtraCharges(eq(COMPANY), any(), any())).thenReturn(List.of(
                new ExtraCharge("chat_session", 120, 4),
                new ExtraCharge("copilot_message", 30, 2),
                new ExtraCharge("reanalysis", 15, 1),
                new ExtraCharge("something_new", 5, 1)));

        CostReportResponse report = service.findCostReport(COMPANY, null, null);

        Map<CostComponent, Long> lines = report.components().stream()
                .collect(Collectors.toMap(CostComponentDto::key, CostComponentDto::uzs));
        assertThat(lines.get(CostComponent.LLM_INPUT)).isEqualTo(40);
        assertThat(lines.get(CostComponent.LLM_OUTPUT)).isEqualTo(30);
        assertThat(lines.get(CostComponent.LLM_CACHED)).isEqualTo(5);
        assertThat(lines.get(CostComponent.CHAT)).isEqualTo(120);
        assertThat(lines.get(CostComponent.COPILOT)).isEqualTo(30);
        assertThat(lines.get(CostComponent.CALL_QUESTION)).isZero();
        assertThat(lines.get(CostComponent.REANALYSIS)).isEqualTo(15);
        assertThat(lines.get(CostComponent.OTHER_AI)).isEqualTo(5);
        assertThat(report.totalUzs()).isEqualTo(2069 + 120 + 30 + 15 + 5);
        // Rates are what the company pays: provider rates +20%, the platform fee as is.
        assertThat(report.rates().inputPerMillionUzs()).isEqualTo(1200);
        assertThat(report.rates().outputPerMillionUzs()).isEqualTo(4800);
        assertThat(report.rates().platformFeePerMinuteUzs()).isEqualTo(100);
    }

    /** Default period is this month so far, and every day is present so the chart has no gaps. */
    @Test
    void fillsEveryDayOfTheDefaultPeriod() {
        when(callBilling.findCostTotals(eq(COMPANY), any(), any())).thenReturn(CostTotals.NONE);
        when(callBilling.findDailySpend(eq(COMPANY), any(), any()))
                .thenReturn(List.of(new DailySpend(LocalDate.of(2026, 11, 3), 500)));
        when(ledger.findDailyExtraSpend(eq(COMPANY), any(), any()))
                .thenReturn(List.of(new DailySpend(LocalDate.of(2026, 11, 3), 25)));

        CostReportResponse report = service.findCostReport(COMPANY, null, null);

        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(report.to()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(report.daily()).hasSize(6);
        assertThat(report.daily().get(2).uzs()).isEqualTo(525);
        assertThat(report.daily().get(0).uzs()).isZero();
    }

    /** A model's purposes fold into one row; the dearest model comes first. */
    @Test
    void groupsLlmCostPerModel() {
        when(callBilling.findCostTotals(eq(COMPANY), any(), any())).thenReturn(CostTotals.NONE);
        when(llmUsage.findModelSpend(eq(COMPANY), any(), any())).thenReturn(List.of(
                new ModelSpend("gemini-2.5-flash", LlmUsagePurpose.CALL, true, 1000, 100, 0, 30, 20, 0),
                new ModelSpend("gpt-4o", LlmUsagePurpose.COPILOT, true, 500, 50, 0, 200, 100, 0),
                new ModelSpend("gemini-2.5-flash", LlmUsagePurpose.SUMMARY, true, 400, 40, 0, 10, 5, 0)));

        CostReportResponse report = service.findCostReport(COMPANY, null, null);

        assertThat(report.models()).extracting(m -> m.model()).containsExactly("gpt-4o", "gemini-2.5-flash");
        var flash = report.models().get(1);
        assertThat(flash.inputTokens()).isEqualTo(1400);
        assertThat(flash.totalUzs()).isEqualTo(65);
        assertThat(flash.uzsByPurpose()).containsEntry(LlmUsagePurpose.CALL, 50L)
                .containsEntry(LlmUsagePurpose.SUMMARY, 15L);
    }

    @Test
    void rejectsAPeriodThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> service.findCostReport(COMPANY, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(ValidationException.class);
    }
}
