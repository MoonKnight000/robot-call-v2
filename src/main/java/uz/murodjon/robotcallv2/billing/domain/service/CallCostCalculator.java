package uz.murodjon.robotcallv2.billing.domain.service;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingLlmRates;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingRates;
import uz.murodjon.robotcallv2.billing.domain.entity.CallCostBreakdown;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelLlmCost;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelPrice;
import uz.murodjon.robotcallv2.billing.domain.entity.ModelTokens;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Prices a finished call against a price list.
 *
 * <p>Spring-free and stateless: this is the one piece of billing that a company will
 * argue with, so it has to be testable by handing it two records and reading the answer.
 *
 * <p>The markup applies to what the providers charged — tokens, recognition, synthesis,
 * a realtime engine, the trunk — and not to the platform fee, which is already this
 * platform's own price rather than a cost being resold.
 *
 * <p>A call that ran on a speech-to-speech engine is priced on that engine's minutes
 * instead of tokens, recognition and synthesis: those three happened inside the one
 * vendor, and the tokens it reports are audio tokens that the text-LLM rate does not fit.
 */
public final class CallCostCalculator {

    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);
    private static final BigDecimal TOKENS_PER_MILLION = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal CHARS_PER_THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal BASIS_POINTS = BigDecimal.valueOf(10_000);

    private CallCostCalculator() {
    }

    /** Every model's tokens at the common LLM rate. */
    public static CallCostBreakdown calculate(CallUsage usage, BillingRates rates) {
        return calculate(usage, rates, model -> null);
    }

    /**
     * @param priceOf the price-list row a reported model id belongs to, or null — that model's
     *                tokens are then priced at {@code rates.llm()}, the common rate
     */
    public static CallCostBreakdown calculate(CallUsage usage, BillingRates rates,
                                              Function<String, ModelPrice> priceOf) {
        if (usage == null || rates == null) {
            return CallCostBreakdown.FREE;
        }

        BigDecimal minutes = minutes(usage.durationSec());

        List<ModelLlmCost> llmByModel = new ArrayList<>();
        BigDecimal stt = BigDecimal.ZERO;
        BigDecimal tts = BigDecimal.ZERO;
        BigDecimal realtime = BigDecimal.ZERO;
        if (usage.realtime()) {
            realtime = minutes(usage.realtimeSeconds())
                    .multiply(BigDecimal.valueOf(realtimePerMinuteUzs(usage.realtimeModel(), rates, priceOf)));
            // The engine's own tokens carry no model and are covered by its minutes. A named model
            // is a separate text model the engine handed its reasoning to (GPT-Live's delegation),
            // which the vendor bills per token on top of the minutes.
            for (ModelTokens tokens : usage.llmByModel()) {
                if (tokens.model() != null && !tokens.model().isBlank()) {
                    llmByModel.add(priceModel(tokens, rates, priceOf));
                }
            }
        } else {
            for (ModelTokens tokens : usage.tokensToPrice()) {
                llmByModel.add(priceModel(tokens, rates, priceOf));
            }
            stt = minutes.multiply(BigDecimal.valueOf(rates.sttPerMinuteUzs()));
            tts = BigDecimal.valueOf(usage.ttsChars())
                    .divide(CHARS_PER_THOUSAND, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(rates.ttsPer1kCharsUzs()));
        }
        stt = stt.add(minutes(usage.extraSttSeconds()).multiply(BigDecimal.valueOf(rates.sttPerMinuteUzs())));
        BigDecimal telephony = minutes.multiply(BigDecimal.valueOf(rates.telephonyPerMinuteUzs()));

        // Each model's token lines rounded on their own and the LLM line summed from them, so the
        // per-model and input / output / cached split shown to a company adds up to it exactly.
        long llmPromptUzs = llmByModel.stream().mapToLong(ModelLlmCost::promptUzs).sum();
        long llmCompletionUzs = llmByModel.stream().mapToLong(ModelLlmCost::completionUzs).sum();
        long llmCachedUzs = llmByModel.stream().mapToLong(ModelLlmCost::cachedUzs).sum();
        long llmUzs = llmPromptUzs + llmCompletionUzs + llmCachedUzs;
        long sttUzs = withMarkup(stt, rates.markupBasisPoints());
        long ttsUzs = withMarkup(tts, rates.markupBasisPoints());
        long realtimeUzs = withMarkup(realtime, rates.markupBasisPoints());
        long telephonyUzs = withMarkup(telephony, rates.markupBasisPoints());
        long platformFeeUzs = round(minutes.multiply(BigDecimal.valueOf(rates.platformFeePerMinuteUzs())));

        // Summed from the rounded lines, not rounded separately: an invoice the customer
        // adds up by hand has to reach the same total.
        long total = llmUzs + sttUzs + ttsUzs + realtimeUzs + telephonyUzs + platformFeeUzs;

        return new CallCostBreakdown(llmUzs, llmPromptUzs, llmCompletionUzs, llmCachedUzs,
                sttUzs, ttsUzs, realtimeUzs, telephonyUzs, platformFeeUzs, total, llmByModel);
    }

    /** One model's tokens at its own price when the list has it, else at the common rate. */
    private static ModelLlmCost priceModel(ModelTokens tokens, BillingRates rates,
                                           Function<String, ModelPrice> priceOf) {
        ModelPrice price = tokens.model() == null ? null : priceOf.apply(tokens.model());
        BillingLlmRates llm = price != null ? price.llm() : rates.llm();
        String model = price != null ? price.model() : ModelPriceMatcher.normalize(tokens.model());
        int markup = rates.markupBasisPoints();
        return new ModelLlmCost(model, price != null, tokens, llm,
                withMarkup(perMillion(tokens.billablePromptTokens(), llm.promptPerMillionUzs()), markup),
                withMarkup(perMillion(tokens.completionTokens(), llm.completionPerMillionUzs()), markup),
                withMarkup(perMillion(tokens.cachedTokens(), llm.cachedPromptPerMillionUzs()), markup));
    }

    /** The engine model's own per-minute price when the list has one, else the common realtime rate. */
    private static long realtimePerMinuteUzs(String model, BillingRates rates, Function<String, ModelPrice> priceOf) {
        ModelPrice price = model == null ? null : priceOf.apply(model);
        return price != null && price.realtimePerMinuteUzs() != null
                ? price.realtimePerMinuteUzs() : rates.realtimePerMinuteUzs();
    }

    /**
     * What one minute of a call costs before its tokens and speech: the trunk, recognition
     * or the realtime engine, and the platform fee. The floor a call's length is budgeted
     * against when the balance is enforced — the rest is only known once the call ends.
     */
    public static long minimumPerMinuteUzs(BillingRates rates, boolean realtime) {
        if (rates == null) {
            return 0;
        }
        long engine = realtime ? rates.realtimePerMinuteUzs() : rates.sttPerMinuteUzs();
        return withMarkup(BigDecimal.valueOf(rates.telephonyPerMinuteUzs() + engine), rates.markupBasisPoints())
                + rates.platformFeePerMinuteUzs();
    }

    /** A provider rate as the company pays it: with the markup, rounded to whole UZS. */
    public static long applyMarkup(long providerRateUzs, int markupBasisPoints) {
        return withMarkup(BigDecimal.valueOf(providerRateUzs), markupBasisPoints);
    }

    private static BigDecimal minutes(int seconds) {
        return BigDecimal.valueOf(seconds).divide(SECONDS_PER_MINUTE, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal perMillion(long tokens, long pricePerMillionUzs) {
        return BigDecimal.valueOf(tokens)
                .divide(TOKENS_PER_MILLION, 9, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(pricePerMillionUzs));
    }

    private static long withMarkup(BigDecimal cost, int markupBasisPoints) {
        BigDecimal multiplier = BASIS_POINTS.add(BigDecimal.valueOf(markupBasisPoints))
                .divide(BASIS_POINTS, 6, RoundingMode.HALF_UP);
        return round(cost.multiply(multiplier));
    }

    private static long round(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
