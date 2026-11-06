package uz.murodjon.robotcallv2.billing.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.billing.application.dto.*;
import uz.murodjon.robotcallv2.billing.application.port.input.BillingCostUseCase;
import uz.murodjon.robotcallv2.billing.application.port.output.BillingLedgerRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.CallBillingRepository;
import uz.murodjon.robotcallv2.billing.application.port.output.LlmUsageRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingRates;
import uz.murodjon.robotcallv2.billing.domain.entity.CostTotals;
import uz.murodjon.robotcallv2.billing.domain.entity.DailySpend;
import uz.murodjon.robotcallv2.billing.domain.entity.ExtraCharge;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelSpend;
import uz.murodjon.robotcallv2.billing.domain.enums.CostComponent;
import uz.murodjon.robotcallv2.billing.domain.enums.LlmUsagePurpose;
import uz.murodjon.robotcallv2.billing.domain.service.CallCostCalculator;
import uz.murodjon.robotcallv2.billing.infrastructure.config.BillingRateProperties;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The billing screen's cost report: every som a company spent in a period, split into the
 * lines it was priced on.
 *
 * <p>Two sources make the total. Calls come from their billing rows, which carry each line
 * already priced. Model tokens charged outside a call — chat, copilot, questions about a
 * call, re-analysis — exist only in the ledger, and leaving them out is how the screen used
 * to show less than the balance actually lost.
 */
@Service
public class BillingCostService implements BillingCostUseCase {

    /** Billing periods are keyed on Tashkent days, like the monthly chart and the allowances. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private static final int MAX_DAYS = 366;

    /** Ledger {@code reference_type} of an extra charge → its line; see CallBillingService. */
    private static final Map<String, CostComponent> EXTRA_SOURCES = Map.of(
            "chat_session", CostComponent.CHAT,
            "copilot_message", CostComponent.COPILOT,
            "call_attempt", CostComponent.CALL_QUESTION,
            "reanalysis", CostComponent.REANALYSIS);

    private final CallBillingRepository callBillingRepository;
    private final BillingLedgerRepository billingLedgerRepository;
    private final LlmUsageRepository llmUsageRepository;
    private final BillingRateProperties billingRateProperties;
    private final Clock clock;

    public BillingCostService(CallBillingRepository callBillingRepository,
                              BillingLedgerRepository billingLedgerRepository,
                              LlmUsageRepository llmUsageRepository,
                              BillingRateProperties billingRateProperties,
                              Clock clock) {
        this.callBillingRepository = callBillingRepository;
        this.billingLedgerRepository = billingLedgerRepository;
        this.llmUsageRepository = llmUsageRepository;
        this.billingRateProperties = billingRateProperties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CostReportResponse findCostReport(long companyId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZONE);
        LocalDate first = from != null ? from : today.withDayOfMonth(1);
        LocalDate last = to != null ? to : today;
        if (last.isBefore(first)) {
            throw new ValidationException(ErrorCode.DATE_RANGE_INVALID);
        }
        long days = ChronoUnit.DAYS.between(first, last) + 1;
        if (days > MAX_DAYS) {
            throw new ValidationException(ErrorCode.BILLING_COST_RANGE_TOO_LONG, MAX_DAYS, days);
        }
        Instant start = first.atStartOfDay(ZONE).toInstant();
        Instant end = last.plusDays(1).atStartOfDay(ZONE).toInstant();

        CostTotals calls = callBillingRepository.findCostTotals(companyId, start, end);
        List<ExtraCharge> extras = billingLedgerRepository.findExtraCharges(companyId, start, end);

        List<CostComponentDto> components = buildComponents(calls, extras);
        long totalUzs = components.stream().mapToLong(CostComponentDto::uzs).sum();

        return new CostReportResponse(first, last, totalUzs,
                new CostUsageDto(calls.calls(), calls.callSeconds(), calls.sttSeconds(), calls.realtimeSeconds(),
                        calls.inputTokens(), calls.outputTokens(), calls.cachedTokens(), calls.ttsChars()),
                components,
                buildRates(),
                buildDaily(companyId, first, last, start, end),
                buildModels(llmUsageRepository.findModelSpend(companyId, start, end)));
    }

    /** One row per model (unknown ones together), its purposes folded in, most expensive first. */
    private static List<ModelCostDto> buildModels(List<ModelSpend> spend) {
        Map<String, List<ModelSpend>> byModel = new LinkedHashMap<>();
        for (ModelSpend row : spend) {
            byModel.computeIfAbsent(row.model(), key -> new ArrayList<>()).add(row);
        }
        List<ModelCostDto> models = new ArrayList<>();
        byModel.forEach((model, rows) -> {
            Map<LlmUsagePurpose, Long> byPurpose = new EnumMap<>(LlmUsagePurpose.class);
            long input = 0, output = 0, cached = 0, inputUzs = 0, outputUzs = 0, cachedUzs = 0;
            boolean listed = false;
            for (ModelSpend row : rows) {
                input += row.inputTokens();
                output += row.outputTokens();
                cached += row.cachedTokens();
                inputUzs += row.inputUzs();
                outputUzs += row.outputUzs();
                cachedUzs += row.cachedUzs();
                listed |= row.listed();
                byPurpose.merge(row.purpose(), row.inputUzs() + row.outputUzs() + row.cachedUzs(), Long::sum);
            }
            models.add(new ModelCostDto(model, listed, input, output, cached, inputUzs, outputUzs, cachedUzs,
                    inputUzs + outputUzs + cachedUzs, byPurpose));
        });
        models.sort(Comparator.comparingLong(ModelCostDto::totalUzs).reversed());
        return models;
    }

    /** Every line, zero ones included, in the enum's order — the screen does not have to guess which exist. */
    private static List<CostComponentDto> buildComponents(CostTotals calls, List<ExtraCharge> extras) {
        Map<CostComponent, long[]> extraByLine = new EnumMap<>(CostComponent.class);
        for (ExtraCharge extra : extras) {
            CostComponent line = EXTRA_SOURCES.getOrDefault(extra.source(), CostComponent.OTHER_AI);
            long[] sum = extraByLine.computeIfAbsent(line, key -> new long[2]);
            sum[0] += extra.uzs();
            sum[1] += extra.count();
        }

        List<CostComponentDto> lines = new ArrayList<>();
        lines.add(new CostComponentDto(CostComponent.LLM_INPUT, calls.llmPromptUzs(), null));
        lines.add(new CostComponentDto(CostComponent.LLM_OUTPUT, calls.llmCompletionUzs(), null));
        lines.add(new CostComponentDto(CostComponent.LLM_CACHED, calls.llmCachedUzs(), null));
        lines.add(new CostComponentDto(CostComponent.STT, calls.sttUzs(), null));
        lines.add(new CostComponentDto(CostComponent.TTS, calls.ttsUzs(), null));
        lines.add(new CostComponentDto(CostComponent.REALTIME, calls.realtimeUzs(), null));
        lines.add(new CostComponentDto(CostComponent.TELEPHONY, calls.telephonyUzs(), null));
        lines.add(new CostComponentDto(CostComponent.PLATFORM_FEE, calls.platformFeeUzs(), null));
        for (CostComponent line : List.of(CostComponent.CHAT, CostComponent.COPILOT, CostComponent.CALL_QUESTION,
                CostComponent.REANALYSIS, CostComponent.OTHER_AI)) {
            long[] sum = extraByLine.getOrDefault(line, new long[2]);
            lines.add(new CostComponentDto(line, sum[0], sum[1]));
        }
        return lines;
    }

    private CostRatesDto buildRates() {
        BillingRates rates = billingRateProperties.rates();
        int markup = rates.markupBasisPoints();
        return new CostRatesDto(
                billingRateProperties.version(),
                CallCostCalculator.applyMarkup(rates.llm().promptPerMillionUzs(), markup),
                CallCostCalculator.applyMarkup(rates.llm().completionPerMillionUzs(), markup),
                CallCostCalculator.applyMarkup(rates.llm().cachedPromptPerMillionUzs(), markup),
                CallCostCalculator.applyMarkup(rates.sttPerMinuteUzs(), markup),
                CallCostCalculator.applyMarkup(rates.ttsPer1kCharsUzs(), markup),
                CallCostCalculator.applyMarkup(rates.realtimePerMinuteUzs(), markup),
                CallCostCalculator.applyMarkup(rates.telephonyPerMinuteUzs(), markup),
                // The platform fee is this platform's own price and carries no markup.
                rates.platformFeePerMinuteUzs());
    }

    /** Calls and extra charges per day, with every day of the period present so a chart has no gaps. */
    private List<DailySpendDto> buildDaily(long companyId, LocalDate first, LocalDate last,
                                           Instant start, Instant end) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (DailySpend day : callBillingRepository.findDailySpend(companyId, start, end)) {
            byDay.merge(day.day(), day.uzs(), Long::sum);
        }
        for (DailySpend day : billingLedgerRepository.findDailyExtraSpend(companyId, start, end)) {
            byDay.merge(day.day(), day.uzs(), Long::sum);
        }
        List<DailySpendDto> daily = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            daily.add(new DailySpendDto(day, byDay.getOrDefault(day, 0L)));
        }
        return daily;
    }
}
