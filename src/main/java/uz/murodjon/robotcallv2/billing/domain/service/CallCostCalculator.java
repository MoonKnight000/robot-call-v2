package uz.murodjon.robotcallv2.billing.domain.service;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingRates;
import uz.murodjon.robotcallv2.billing.domain.entity.CallCostBreakdown;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    public static CallCostBreakdown calculate(CallUsage usage, BillingRates rates) {
        if (usage == null || rates == null) {
            return CallCostBreakdown.FREE;
        }

        BigDecimal minutes = minutes(usage.durationSec());

        BigDecimal llm = BigDecimal.ZERO;
        BigDecimal stt = BigDecimal.ZERO;
        BigDecimal tts = BigDecimal.ZERO;
        BigDecimal realtime = BigDecimal.ZERO;
        if (usage.realtime()) {
            realtime = minutes(usage.realtimeSeconds()).multiply(BigDecimal.valueOf(rates.realtimePerMinuteUzs()));
        } else {
            llm = perMillion(usage.billablePromptTokens(), rates.llm().promptPerMillionUzs())
                    .add(perMillion(usage.completionTokens(), rates.llm().completionPerMillionUzs()))
                    .add(perMillion(usage.cachedTokens(), rates.llm().cachedPromptPerMillionUzs()));
            stt = minutes.multiply(BigDecimal.valueOf(rates.sttPerMinuteUzs()));
            tts = BigDecimal.valueOf(usage.ttsChars())
                    .divide(CHARS_PER_THOUSAND, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(rates.ttsPer1kCharsUzs()));
        }
        BigDecimal telephony = minutes.multiply(BigDecimal.valueOf(rates.telephonyPerMinuteUzs()));

        long llmUzs = withMarkup(llm, rates.markupBasisPoints());
        long sttUzs = withMarkup(stt, rates.markupBasisPoints());
        long ttsUzs = withMarkup(tts, rates.markupBasisPoints());
        long realtimeUzs = withMarkup(realtime, rates.markupBasisPoints());
        long telephonyUzs = withMarkup(telephony, rates.markupBasisPoints());
        long platformFeeUzs = round(minutes.multiply(BigDecimal.valueOf(rates.platformFeePerMinuteUzs())));

        // Summed from the rounded lines, not rounded separately: an invoice the customer
        // adds up by hand has to reach the same total.
        long total = llmUzs + sttUzs + ttsUzs + realtimeUzs + telephonyUzs + platformFeeUzs;

        return new CallCostBreakdown(llmUzs, sttUzs, ttsUzs, realtimeUzs, telephonyUzs, platformFeeUzs, total);
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
